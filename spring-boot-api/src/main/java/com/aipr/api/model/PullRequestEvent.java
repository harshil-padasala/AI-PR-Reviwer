package com.aipr.api.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents a single GitHub pull_request webhook event that has been
 * accepted for review. One row is created per "opened" / "synchronize"
 * action so we keep a full audit trail of what was reviewed and when.
 */
@Entity
@Table(name = "pull_request_events")
public class PullRequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String repoFullName; // e.g. "octocat/hello-world"

    @Column(nullable = false)
    private Integer prNumber;

    @Column(nullable = false)
    private String action; // opened, synchronize, reopened

    @Column(nullable = false)
    private String headSha;

    @Column(nullable = false)
    private String diffUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    @Column(nullable = false)
    private Instant receivedAt;

    public enum ReviewStatus {
        QUEUED, IN_PROGRESS, COMPLETED, FAILED
    }

    public PullRequestEvent() {
    }

    public PullRequestEvent(String repoFullName, Integer prNumber, String action,
                             String headSha, String diffUrl) {
        this.repoFullName = repoFullName;
        this.prNumber = prNumber;
        this.action = action;
        this.headSha = headSha;
        this.diffUrl = diffUrl;
        this.status = ReviewStatus.QUEUED;
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRepoFullName() { return repoFullName; }
    public void setRepoFullName(String repoFullName) { this.repoFullName = repoFullName; }

    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getHeadSha() { return headSha; }
    public void setHeadSha(String headSha) { this.headSha = headSha; }

    public String getDiffUrl() { return diffUrl; }
    public void setDiffUrl(String diffUrl) { this.diffUrl = diffUrl; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
