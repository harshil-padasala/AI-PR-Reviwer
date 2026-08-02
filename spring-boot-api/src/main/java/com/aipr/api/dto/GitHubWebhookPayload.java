package com.aipr.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal mapping of the GitHub "pull_request" webhook event payload.
 * We only pull out the fields we actually need; everything else is ignored.
 * https://docs.github.com/en/webhooks/webhook-events-and-payloads#pull_request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubWebhookPayload {

    private String action;

    @JsonProperty("number")
    private Integer prNumber;

    @JsonProperty("pull_request")
    private PullRequest pullRequest;

    private Repository repository;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequest {
        @JsonProperty("diff_url")
        private String diffUrl;

        private Head head;

        public String getDiffUrl() { return diffUrl; }
        public void setDiffUrl(String diffUrl) { this.diffUrl = diffUrl; }
        public Head getHead() { return head; }
        public void setHead(Head head) { this.head = head; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Head {
        private String sha;
        public String getSha() { return sha; }
        public void setSha(String sha) { this.sha = sha; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        @JsonProperty("full_name")
        private String fullName;
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }

    public PullRequest getPullRequest() { return pullRequest; }
    public void setPullRequest(PullRequest pullRequest) { this.pullRequest = pullRequest; }

    public Repository getRepository() { return repository; }
    public void setRepository(Repository repository) { this.repository = repository; }
}
