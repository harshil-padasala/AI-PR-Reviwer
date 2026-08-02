package com.aipr.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Thin client around Azure OpenAI's chat completions endpoint.
 * Forces the model to return strict JSON so ReviewParser can deserialize
 * it reliably instead of scraping free-form text.
 */
public class AiReviewClient {

	private static final MediaType JSON = MediaType.get("application/json");

	private static final String SYSTEM_PROMPT = """
			You are an experienced Staff Software Engineer performing a professional pull request review.
			
			Your goal is to identify issues that could affect correctness, reliability, security, maintainability, scalability, performance, readability, and long-term code quality.
			
			Review only the code changes provided in the diff. Do not assume code outside the diff unless it is clearly referenced.
			
			Focus on meaningful feedback. Do NOT make trivial style comments unless they affect readability or maintainability.
			
			Review the following aspects:
			
			1. Correctness
			- Logic errors
			- Edge cases
			- Null handling
			- Race conditions
			- Incorrect assumptions
			- Resource leaks
			- API misuse
			
			2. Security
			- SQL Injection
			- Command Injection
			- XSS
			- CSRF
			- Authentication
			- Authorization
			- Sensitive data exposure
			- Secret leakage
			- Unsafe deserialization
			- Path traversal
			- SSRF
			- Input validation
			
			3. Performance
			- Inefficient algorithms
			- N+1 queries
			- Memory usage
			- Unnecessary object creation
			- Blocking operations
			- Excessive database calls
			- Expensive loops
			
			4. Concurrency
			- Thread safety
			- Synchronization
			- Deadlocks
			- Shared mutable state
			- Atomicity
			
			5. Reliability
			- Exception handling
			- Logging
			- Retry logic
			- Timeout handling
			- Transaction handling
			- Resource cleanup
			
			6. Maintainability
			- Duplicate code
			- Large methods
			- Poor abstraction
			- Magic numbers
			- Tight coupling
			- SOLID violations
			
			7. Readability
			- Confusing names
			- Complex logic
			- Nested conditionals
			- Missing comments where necessary
			
			8. Architecture
			- Separation of concerns
			- Layer violations
			- Dependency direction
			- Domain modeling
			
			9. Testing
			- Missing unit tests
			- Missing integration tests
			- Missing edge-case coverage
			
			10. Best Practices
			- Language best practices
			- Framework best practices
			- Design patterns where appropriate
			
			For every issue:
			
			- Explain WHY it is a problem.
			- Explain WHEN it could fail.
			- Suggest a concrete fix.
			- Mention the affected file and line if available.
			
			Avoid:
			- Personal opinions
			- Style-only comments
			- Speculation
			- Duplicate findings
			- False positives
			
			Only report issues that have a reasonable likelihood of being real.
			
			Assign one severity:
			
			CRITICAL
			HIGH
			MEDIUM
			LOW
			INFO
			
			Severity guidelines:
			
			CRITICAL
			- Security vulnerabilities
			- Data corruption
			- Major production failures
			
			HIGH
			- Bugs likely to occur
			- Concurrency bugs
			- Resource leaks
			- Significant performance issues
			
			MEDIUM
			- Maintainability
			- Missing validation
			- Reliability concerns
			
			LOW
			- Readability
			- Minor refactoring
			
			INFO
			- Suggestions
			- Optional improvements
			
			            Respond ONLY with valid JSON in the following format:
			
			            {
			              "summary": "Overall review",
			              "comments": [
			                {
			                  "file": "src/File.java",
			                  "line": 42,
			                  "severity": "high|medium|low|nit",
			                  "comment": "Explanation"
			                }
			              ]
			            }
			
			            If there are no issues, return an empty comments array.
			""";

	private final OkHttpClient client;
	private final ObjectMapper mapper = new ObjectMapper();

	private final String endpoint;
	private final String deployment;
	private final String apiKey;
	private final String apiVersion;

	public AiReviewClient(
			String endpoint,
			String deployment,
			String apiKey,
			String apiVersion) {

		this.endpoint = endpoint;
		this.deployment = deployment;
		this.apiKey = apiKey;
		this.apiVersion = apiVersion;

		this.client = new OkHttpClient.Builder()
				.connectTimeout(10, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.build();
	}

	public String reviewDiff(String diff) throws IOException {

		ObjectNode body = mapper.createObjectNode();

		var messages = mapper.createArrayNode();

		ObjectNode system = mapper.createObjectNode();
		system.put("role", "system");
		system.put("content", SYSTEM_PROMPT);

		ObjectNode user = mapper.createObjectNode();
		user.put("role", "user");
		user.put("content", "Review this git diff:\n\n" + diff);

		messages.add(system);
		messages.add(user);

		body.set("messages", messages);

		// GPT-5 uses max_completion_tokens
		body.put("max_completion_tokens", 500);

		// JSON mode
		ObjectNode responseFormat = mapper.createObjectNode();
		responseFormat.put("type", "json_object");
		body.set("response_format", responseFormat);

		String url = String.format(
				"%s/openai/deployments/%s/chat/completions?api-version=%s",
				endpoint,
				deployment,
				apiVersion);

		Request request = new Request.Builder()
				.url(url)
				.header("api-key", apiKey)
				.post(RequestBody.create(body.toString(), JSON))
				.build();

		String responseBody = executeWithRetry(request, body);

		JsonNode root = mapper.readTree(responseBody);

		JsonNode content = root.path("choices")
				.path(0)
				.path("message")
				.path("content");

		// Older Chat Completions format
		if (content.isTextual()) {
			return content.asText();
		}

		// Newer GPT-5 format
		if (content.isArray()
				&& !content.isEmpty()
				&& content.get(0).has("text")) {
			return content.get(0).get("text").asText();
		}

		throw new IOException(
				"Unexpected response format:\n" + responseBody);
	}

	private static final int MAX_RETRIES = 4;

	private String executeWithRetry(Request request, ObjectNode body) throws IOException {

		IOException lastError = null;

		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {

			try (Response response = client.newCall(request).execute()) {

				String responseBody =
						response.body() != null
								? response.body().string()
								: "";

				System.out.println("Request URL:");
				System.out.println("--------------------------------------");
				System.out.println(body.toPrettyString());
				System.out.println("HTTP Status: " + response.code());
				System.out.println(responseBody);

				if (response.isSuccessful()) {
					return responseBody;
				}

				boolean retryable = response.code() == 429 || response.code() >= 500;

				if (!retryable || attempt == MAX_RETRIES) {
					throw new IOException(
							"Azure OpenAI call failed: HTTP "
									+ response.code()
									+ "\n"
									+ responseBody);
				}

				lastError = new IOException(
						"Azure OpenAI call failed: HTTP " + response.code() + "\n" + responseBody);

				sleep(retryDelayMillis(response, attempt));
			}
		}

		throw lastError;
	}

	private static long retryDelayMillis(Response response, int attempt) {

		String retryAfter = response.header("Retry-After");

		if (retryAfter != null) {
			try {
				return Long.parseLong(retryAfter.trim()) * 1000L;
			} catch (NumberFormatException ignored) {
				// fall through to exponential backoff
			}
		}

		return 1000L * (1L << attempt);
	}

	private static void sleep(long millis) throws IOException {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Retry wait interrupted", e);
		}
	}
}