package com.aipr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload the Azure Function POSTs back to the Spring Boot API once the
 * LLM review is complete. `commentsJson` is the raw structured JSON array
 * produced by the model (already validated/parsed by the Function).
 */
public class ReviewCallbackRequest {

    @NotNull
    private Long pullRequestEventId;

    @NotBlank
    private String summary;

    @NotBlank
    private String commentsJson;

    @NotNull
    private Integer issuesFound;

    private boolean success = true;

    private String errorMessage;

    public Long getPullRequestEventId() { return pullRequestEventId; }
    public void setPullRequestEventId(Long pullRequestEventId) { this.pullRequestEventId = pullRequestEventId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCommentsJson() { return commentsJson; }
    public void setCommentsJson(String commentsJson) { this.commentsJson = commentsJson; }

    public Integer getIssuesFound() { return issuesFound; }
    public void setIssuesFound(Integer issuesFound) { this.issuesFound = issuesFound; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
