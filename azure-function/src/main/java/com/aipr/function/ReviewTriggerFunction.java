package com.aipr.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point: triggered whenever the Spring Boot API publishes a
 * "review requested" message onto the Service Bus queue.
 *
 * Flow:
 *   1. Parse the queue message (pullRequestEventId, repo, PR number, diff URL)
 *   2. Fetch the PR's unified diff from GitHub
 *   3. Send the diff to the LLM (Azure OpenAI) for review
 *   4. Parse the model's structured JSON response
 *   5. Post the review as inline comments back onto the PR
 *   6. Callback to the Spring Boot API with the result for persistence
 *
 * Configure these Application Settings (or local.settings.json locally):
 *   GITHUB_API_TOKEN, AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_DEPLOYMENT,
 *   AZURE_OPENAI_API_KEY, AZURE_OPENAI_API_VERSION, SPRING_API_CALLBACK_URL
 */
public class ReviewTriggerFunction {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_DIFF_CHARS = 40_000; // guard against huge diffs / token limits

    @FunctionName("ReviewTriggerFunction")
    public void run(
            @ServiceBusQueueTrigger(
                    name = "message",
                    queueName = "pr-review-requests",
                    connection = "AZURE_SERVICEBUS_CONNECTION_STRING")
            String message,
            final ExecutionContext context) {

        Logger logger = context.getLogger();
        ObjectMapper mapper = new ObjectMapper();

        String githubToken = System.getenv("GITHUB_API_TOKEN");
        String callbackUrl = System.getenv("SPRING_API_CALLBACK_URL");

        Long pullRequestEventId = null;
        String repoFullName = null;
        Integer prNumber = null;
        String headSha = null;

        try {
            JsonNode payload = mapper.readTree(message);
            pullRequestEventId = payload.path("pullRequestEventId").asLong();
            repoFullName = payload.path("repoFullName").asText();
            prNumber = payload.path("prNumber").asInt();
            headSha = payload.path("headSha").asText();

            logger.info("Processing review for " + repoFullName + "#" + prNumber);

            // 1. Fetch diff
            GitHubDiffFetcher diffFetcher = new GitHubDiffFetcher(githubToken);
            String rawDiff = diffFetcher.fetchDiff(repoFullName, prNumber);
            String diff = diffFetcher.truncateDiff(rawDiff, MAX_DIFF_CHARS);

            // 2. Call the LLM
            AiReviewClient aiClient = new AiReviewClient(
                    System.getenv("AZURE_OPENAI_ENDPOINT"),
                    System.getenv("AZURE_OPENAI_DEPLOYMENT"),
                    System.getenv("AZURE_OPENAI_API_KEY"),
                    System.getenv().getOrDefault("AZURE_OPENAI_API_VERSION", "2024-06-01"));
            String rawModelOutput = aiClient.reviewDiff(diff);

            // 3. Parse structured output
            ReviewParser parser = new ReviewParser();
            ReviewParser.ParsedReview parsed = parser.parse(rawModelOutput);

            // 4. Post back to GitHub as a review
            GitHubCommentPoster commentPoster = new GitHubCommentPoster(githubToken);
            commentPoster.postReview(repoFullName, prNumber, headSha, parsed.summary, parsed.comments);

            // 5. Callback to Spring Boot API with the result
            sendCallback(callbackUrl, mapper, pullRequestEventId, parsed, true, null);

            logger.info("Completed review for " + repoFullName + "#" + prNumber +
                    " with " + parsed.comments.size() + " comment(s)");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Review pipeline failed for eventId=" + pullRequestEventId, e);
            try {
                sendCallback(callbackUrl, mapper, pullRequestEventId,
                        new ReviewParser.ParsedReview("Review failed.", List.of()), false, e.getMessage());
            } catch (Exception callbackError) {
                logger.log(Level.SEVERE, "Also failed to report failure back to API", callbackError);
            }
        }
    }

    private void sendCallback(String callbackUrl, ObjectMapper mapper, Long pullRequestEventId,
                               ReviewParser.ParsedReview parsed, boolean success, String errorMessage) throws Exception {
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
}
