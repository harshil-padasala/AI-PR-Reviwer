package com.aipr.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * AWS Lambda handler triggered by Amazon SQS events.
 *
 * Flow:
 *   1. Parse each SQS message payload (pullRequestEventId, repoFullName, prNumber, headSha)
 *   2. Fetch the PR's unified diff from GitHub
 *   3. Send the diff to the LLM (OpenAI API) for review
 *   4. Parse the model's structured JSON response
 *   5. Post the review as inline comments back onto the PR
 *   6. Send callback to the Spring Boot API with the result for persistence
 */
public class LambdaHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger log = LoggerFactory.getLogger(LambdaHandler.class);
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_DIFF_CHARS = 40_000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String githubToken;
    private final String callbackUrl;
    private final String openaiEndpoint;
    private final String openaiModel;
    private final String openaiApiKey;

    public LambdaHandler() {
        this.githubToken = getRequiredEnv("GITHUB_API_TOKEN");
        this.callbackUrl = getRequiredEnv("SPRING_API_CALLBACK_URL");
        this.openaiApiKey = getRequiredEnv("OPENAI_API_KEY");
        this.openaiModel = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        this.openaiEndpoint = System.getenv().getOrDefault("OPENAI_ENDPOINT", "https://api.openai.com/v1/chat/completions");
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {
        if (sqsEvent == null || sqsEvent.getRecords() == null) {
            log.info("Received empty SQS event");
            return null;
        }

        for (SQSMessage msg : sqsEvent.getRecords()) {
            processMessage(msg.getBody());
        }
        return null;
    }

    private void processMessage(String messageBody) {
        Long pullRequestEventId = null;
        try {
            JsonNode payload = mapper.readTree(messageBody);
            pullRequestEventId = payload.path("pullRequestEventId").asLong();
            String repoFullName = payload.path("repoFullName").asText();
            int prNumber = payload.path("prNumber").asInt();
            String headSha = payload.path("headSha").asText();

            log.info("Processing review via Lambda for {}#{} (eventId={})", repoFullName, prNumber, pullRequestEventId);

            // 1. Fetch diff
            GitHubDiffFetcher diffFetcher = new GitHubDiffFetcher(githubToken);
            String rawDiff = diffFetcher.fetchDiff(repoFullName, prNumber);
            String diff = diffFetcher.truncateDiff(rawDiff, MAX_DIFF_CHARS);

            // 2. Call LLM
            AiReviewClient aiClient = new AiReviewClient(openaiEndpoint, openaiModel, openaiApiKey);
            String rawModelOutput = aiClient.reviewDiff(diff);

            // 3. Parse output
            ReviewParser parser = new ReviewParser();
            ReviewParser.ParsedReview parsed = parser.parse(rawModelOutput);

            // 4. Post GitHub comments
            GitHubCommentPoster commentPoster = new GitHubCommentPoster(githubToken);
            commentPoster.postReview(repoFullName, prNumber, headSha, parsed.summary, parsed.comments);

            // 5. Callback to Spring Boot API
            sendCallback(pullRequestEventId, parsed, true, null);

            log.info("Successfully completed Lambda review for {}#{} with {} comment(s)", repoFullName, prNumber, parsed.comments.size());

        } catch (Exception e) {
            log.error("Lambda review pipeline failed for eventId={}", pullRequestEventId, e);
            try {
                if (pullRequestEventId != null) {
                    sendCallback(pullRequestEventId, new ReviewParser.ParsedReview("Review failed.", List.of()), false, e.getMessage());
                }
            } catch (Exception callbackError) {
                log.error("Failed to send failure callback to Spring Boot API", callbackError);
            }
            throw new RuntimeException("Failed to process SQS review message", e);
        }
    }

    private void sendCallback(Long pullRequestEventId, ReviewParser.ParsedReview parsed,
                               boolean success, String errorMessage) throws Exception {
        var body = mapper.createObjectNode();
        body.put("pullRequestEventId", pullRequestEventId);
        body.put("summary", parsed.summary);
        body.put("commentsJson", mapper.writeValueAsString(parsed.comments));
        body.put("issuesFound", parsed.comments.size());
        body.put("success", success);
        if (errorMessage != null) {
            body.put("errorMessage", errorMessage);
        }

        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(callbackUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Callback to Spring Boot API failed: HTTP " + response.code());
            }
        }
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            log.warn("Required environment variable '{}' is missing or blank", name);
            return "";
        }
        return value;
    }
}
