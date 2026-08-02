package com.aipr.function;

import com.aipr.function.model.ReviewComment;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Posts the AI's findings back to the pull request:
 *   - one top-level "review" with the summary and a batch of inline
 *     line comments, using GitHub's Pulls Review API so it shows up as
 *     a single grouped review instead of spamming N separate comments.
 * https://docs.github.com/en/rest/pulls/reviews#create-a-review-for-a-pull-request
 */
public class GitHubCommentPoster {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient client;
    private final String githubToken;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubCommentPoster(String githubToken) {
        this.githubToken = githubToken;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void postReview(String repoFullName, int prNumber, String headSha,
                            String summary, List<ReviewComment> comments) throws IOException {
        var body = mapper.createObjectNode();
        body.put("commit_id", headSha);
        body.put("body", "🤖 **AI Code Review**\n\n" + summary);
        body.put("event", "COMMENT"); // use "REQUEST_CHANGES" if any comment severity == "high"

        var commentsArray = mapper.createArrayNode();
        for (ReviewComment c : comments) {
            var node = mapper.createObjectNode();
            node.put("path", c.getFile());
            node.put("line", c.getLine());
            node.put("body", formatComment(c));
            commentsArray.add(node);
        }
        body.set("comments", commentsArray);

        String url = String.format("https://api.github.com/repos/%s/pulls/%d/reviews", repoFullName, prNumber);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Failed to post GitHub review: HTTP " + response.code() + " " + errBody);
            }
        }
    }

    private String formatComment(ReviewComment c) {
        String icon = switch (c.getSeverity().toLowerCase()) {
            case "high" -> "🔴";
            case "medium" -> "🟠";
            case "low" -> "🟡";
            default -> "💬";
        };
        return icon + " **" + c.getSeverity().toUpperCase() + "**: " + c.getComment();
    }
}
