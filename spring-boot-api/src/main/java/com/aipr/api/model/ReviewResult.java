package com.aipr.api.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * The structured result the AWS Lambda worker sends back after the LLM has
 * reviewed a diff. `commentsJson` stores the raw structured array of
 * {file, line, severity, comment} objects the AI produced, so the API
 * doesn't need to know about the AI provider's response shape.
 */
@Entity
@Table(name = "review_results")
public class ReviewResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pullRequestEventId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String commentsJson;

    @Column(nullable = false)
    private Integer issuesFound;

    @Column(nullable = false)
    private Instant createdAt;

    public ReviewResult() {
    }

    public ReviewResult(Long pullRequestEventId, String summary, String commentsJson, Integer issuesFound) {
        this.pullRequestEventId = pullRequestEventId;
        this.summary = summary;
        this.commentsJson = commentsJson;
        this.issuesFound = issuesFound;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPullRequestEventId() { return pullRequestEventId; }
    public void setPullRequestEventId(Long pullRequestEventId) { this.pullRequestEventId = pullRequestEventId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCommentsJson() { return commentsJson; }
    public void setCommentsJson(String commentsJson) { this.commentsJson = commentsJson; }

    public Integer getIssuesFound() { return issuesFound; }
    public void setIssuesFound(Integer issuesFound) { this.issuesFound = issuesFound; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
