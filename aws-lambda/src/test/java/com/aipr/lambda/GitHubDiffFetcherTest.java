package com.aipr.lambda;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubDiffFetcherTest {

    @Test
    public void testTruncateDiffWhenShort() {
        GitHubDiffFetcher fetcher = new GitHubDiffFetcher("dummy-token");
        String diff = "diff --git a/File.java b/File.java\n+hello";
        String truncated = fetcher.truncateDiff(diff, 100);
        assertEquals(diff, truncated);
    }

    @Test
    public void testTruncateDiffWhenExceedsMax() {
        GitHubDiffFetcher fetcher = new GitHubDiffFetcher("dummy-token");
        String diff = "1234567890abcdefghij";
        String truncated = fetcher.truncateDiff(diff, 10);
        assertTrue(truncated.startsWith("1234567890"));
        assertTrue(truncated.contains("[... diff truncated"));
    }
}
