package com.aipr.api.controller;

import com.aipr.api.dto.ReviewCallbackRequest;
import com.aipr.api.model.PullRequestEvent;
import com.aipr.api.model.ReviewResult;
import com.aipr.api.repository.PullRequestEventRepository;
import com.aipr.api.repository.ReviewResultRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Two responsibilities:
 *  - receives the completed-review callback from the Azure Function
 *  - lets a frontend / CLI / recruiter demo fetch review history
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewResultRepository reviewResultRepository;
    private final PullRequestEventRepository pullRequestEventRepository;

    public ReviewController(ReviewResultRepository reviewResultRepository,
                             PullRequestEventRepository pullRequestEventRepository) {
        this.reviewResultRepository = reviewResultRepository;
        this.pullRequestEventRepository = pullRequestEventRepository;
    }

    /** Called by the Azure Function once the LLM review is complete. */
    @PostMapping("/callback")
    public ResponseEntity<Void> receiveReviewResult(@Valid @RequestBody ReviewCallbackRequest request) {
        PullRequestEvent event = pullRequestEventRepository.findById(request.getPullRequestEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown pull request event"));

        if (request.isSuccess()) {
            ReviewResult result = new ReviewResult(
                    event.getId(), request.getSummary(), request.getCommentsJson(), request.getIssuesFound());
            reviewResultRepository.save(result);
            event.setStatus(PullRequestEvent.ReviewStatus.COMPLETED);
            log.info("Review completed for {}#{}: {} issue(s) found",
                    event.getRepoFullName(), event.getPrNumber(), request.getIssuesFound());
        } else {
            event.setStatus(PullRequestEvent.ReviewStatus.FAILED);
            log.error("Review failed for {}#{}: {}",
                    event.getRepoFullName(), event.getPrNumber(), request.getErrorMessage());
        }
        pullRequestEventRepository.save(event);

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ReviewResult> getReview(@PathVariable Long eventId) {
        return reviewResultRepository.findByPullRequestEventId(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<PullRequestEvent>> listReviewedPullRequests() {
        return ResponseEntity.ok(pullRequestEventRepository.findAll());
    }
}
