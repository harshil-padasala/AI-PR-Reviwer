package com.aipr.lambda;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Fetches the unified diff for a pull request directly from GitHub, using
 * the `Accept: application/vnd.github.v3.diff` media type so the API
 * returns raw diff text instead of JSON.
 */
public class GitHubDiffFetcher {

    private final OkHttpClient client;
    private final String githubToken;

    public GitHubDiffFetcher(String githubToken) {
        this.githubToken = githubToken;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * @param repoFullName e.g. "octocat/hello-world"
     * @param prNumber     pull request number
     * @return the raw unified diff text for the PR
     */
    public String fetchDiff(String repoFullName, int prNumber) throws IOException {
        String url = String.format("https://api.github.com/repos/%s/pulls/%d", repoFullName, prNumber);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3.diff")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub diff fetch failed: HTTP " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    /**
     * Diffs can be huge. Truncate defensively so we don't blow the LLM's
     * context window or run up token costs on generated/vendored files.
     */
    public String truncateDiff(String diff, int maxChars) {
        if (diff.length() <= maxChars) {
            return diff;
        }
        return diff.substring(0, maxChars) + "\n\n[... diff truncated, " +
                (diff.length() - maxChars) + " more characters omitted ...]";
    }
}
