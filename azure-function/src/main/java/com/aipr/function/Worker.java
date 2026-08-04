package com.aipr.function;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point: long-running process that consumes the Service Bus queue
 * directly (replaces the previous Azure Functions ServiceBusQueueTrigger)
 * so it can run as a plain container in Kubernetes.
 *
 * Flow (unchanged from the Functions version):
 *   1. Parse the queue message (pullRequestEventId, repo, PR number, diff URL)
 *   2. Fetch the PR's unified diff from GitHub
 *   3. Send the diff to the LLM (Azure OpenAI) for review
 *   4. Parse the model's structured JSON response
 *   5. Post the review as inline comments back onto the PR
 *   6. Callback to the Spring Boot API with the result for persistence
 *
 * Configure via environment variables:
 *   AZURE_SERVICEBUS_CONNECTION_STRING, AZURE_SERVICEBUS_QUEUE_NAME,
 *   GITHUB_API_TOKEN, AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_DEPLOYMENT,
 *   AZURE_OPENAI_API_KEY, AZURE_OPENAI_API_VERSION, SPRING_API_CALLBACK_URL,
 *   HEALTH_PORT (optional, default 8081)
 */
public class Worker {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_DIFF_CHARS = 40_000; // guard against huge diffs / token limits

    private final ObjectMapper mapper = new ObjectMapper();
    private final String githubToken;
    private final String callbackUrl;
    private final String openaiEndpoint;
    private final String openaiDeployment;
    private final String openaiApiKey;
    private final String openaiApiVersion;

    public Worker() {
        this.githubToken = requireEnv("GITHUB_API_TOKEN");
        this.callbackUrl = requireEnv("SPRING_API_CALLBACK_URL");
        this.openaiEndpoint = requireEnv("AZURE_OPENAI_ENDPOINT");
        this.openaiDeployment = requireEnv("AZURE_OPENAI_DEPLOYMENT");
        this.openaiApiKey = requireEnv("AZURE_OPENAI_API_KEY");
        this.openaiApiVersion = System.getenv().getOrDefault("AZURE_OPENAI_API_VERSION", "2024-06-01");
    }

    public static void main(String[] args) throws InterruptedException {
        String connectionString = requireEnv("AZURE_SERVICEBUS_CONNECTION_STRING");
        String queueName = System.getenv().getOrDefault("AZURE_SERVICEBUS_QUEUE_NAME", "pr-review-requests");
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8081"));

        Worker worker = new Worker();
        AtomicBoolean healthy = new AtomicBoolean(false);

        ServiceBusProcessorClient processorClient = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .processor()
                .queueName(queueName)
                .disableAutoComplete()
                .processMessage(worker::handleMessage)
                .processError(Worker::handleError)
                .buildProcessorClient();

        HttpServer healthServer = startHealthServer(healthPort, healthy);

        processorClient.start();
        healthy.set(true);
        log.info("Worker started, consuming queue '{}'", queueName);

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down worker");
            healthy.set(false);
            processorClient.close();
            healthServer.stop(0);
            shutdownLatch.countDown();
        }));
        shutdownLatch.await();
    }

    private static HttpServer startHealthServer(int port, AtomicBoolean healthy) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/healthz", exchange -> {
                int status = healthy.get() ? 200 : 503;
                byte[] body = (healthy.get() ? "OK" : "NOT READY").getBytes();
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.setExecutor(null);
            server.start();
            log.info("Health endpoint listening on :{}/healthz", port);
            return server;
        } catch (Exception e) {
            throw new RuntimeException("Failed to start health server", e);
        }
    }

    private void handleMessage(ServiceBusReceivedMessageContext context) {
        String message = context.getMessage().getBody().toString();

        Long pullRequestEventId = null;
        try {
            JsonNode payload = mapper.readTree(message);
            pullRequestEventId = payload.path("pullRequestEventId").asLong();
            String repoFullName = payload.path("repoFullName").asText();
            int prNumber = payload.path("prNumber").asInt();
            String headSha = payload.path("headSha").asText();

            log.info("Processing review for {}#{}", repoFullName, prNumber);

            // 1. Fetch diff
            GitHubDiffFetcher diffFetcher = new GitHubDiffFetcher(githubToken);
            String rawDiff = diffFetcher.fetchDiff(repoFullName, prNumber);
            String diff = diffFetcher.truncateDiff(rawDiff, MAX_DIFF_CHARS);

            // 2. Call the LLM
            AiReviewClient aiClient = new AiReviewClient(openaiEndpoint, openaiDeployment, openaiApiKey, openaiApiVersion);
            String rawModelOutput = aiClient.reviewDiff(diff);

            // 3. Parse structured output
            ReviewParser parser = new ReviewParser();
            ReviewParser.ParsedReview parsed = parser.parse(rawModelOutput);

            // 4. Post back to GitHub as a review
            GitHubCommentPoster commentPoster = new GitHubCommentPoster(githubToken);
            commentPoster.postReview(repoFullName, prNumber, headSha, parsed.summary, parsed.comments);

            // 5. Callback to Spring Boot API with the result
            sendCallback(pullRequestEventId, parsed, true, null);

            log.info("Completed review for {}#{} with {} comment(s)", repoFullName, prNumber, parsed.comments.size());
            context.complete();

        } catch (Exception e) {
            log.error("Review pipeline failed for eventId={}", pullRequestEventId, e);
            try {
                sendCallback(pullRequestEventId, new ReviewParser.ParsedReview("Review failed.", List.of()), false, e.getMessage());
            } catch (Exception callbackError) {
                log.error("Also failed to report failure back to API", callbackError);
            }
            context.abandon();
        }
    }

    private static void handleError(ServiceBusErrorContext errorContext) {
        log.error("Error occurred on Service Bus entity '{}'", errorContext.getEntityPath(), errorContext.getException());
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

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            log.error("Required environment variable '{}' is missing or blank", name);
            System.exit(1);
        }
        return value;
    }
}
