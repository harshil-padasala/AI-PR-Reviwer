package com.aipr.api.controller;

import com.aipr.api.dto.GitHubWebhookPayload;
import com.aipr.api.model.PullRequestEvent;
import com.aipr.api.repository.PullRequestEventRepository;
import com.aipr.api.service.GitHubWebhookService;
import com.aipr.api.service.QueuePublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Entry point for GitHub's "pull_request" webhook.
 * Configure this URL (https://<host>/api/webhooks/github) in your repo's
 * Settings -> Webhooks, with content type application/json and the same
 * secret configured in `github.webhook.secret`.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final GitHubWebhookService webhookService;
    private final QueuePublisherService queuePublisherService;
    private final PullRequestEventRepository pullRequestEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(GitHubWebhookService webhookService,
                              QueuePublisherService queuePublisherService,
                              PullRequestEventRepository pullRequestEventRepository) {
        this.webhookService = webhookService;
        this.queuePublisherService = queuePublisherService;
        this.pullRequestEventRepository = pullRequestEventRepository;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody String rawPayload) throws Exception {

        if (!webhookService.isValidSignature(rawPayload, signature)) {
            log.warn("Rejected webhook delivery with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        if (!"pull_request".equals(eventType)) {
            // We only care about PR events; ack anything else so GitHub doesn't retry.
            return ResponseEntity.ok("ignored event type: " + eventType);
        }

        GitHubWebhookPayload payload = objectMapper.readValue(rawPayload, GitHubWebhookPayload.class);

        if (!webhookService.isReviewableAction(payload.getAction())) {
            return ResponseEntity.ok("ignored action: " + payload.getAction());
        }

        PullRequestEvent event = new PullRequestEvent(
                payload.getRepository().getFullName(),
                payload.getPrNumber(),
                payload.getAction(),
                payload.getPullRequest().getHead().getSha(),
                payload.getPullRequest().getDiffUrl()
        );
        event = pullRequestEventRepository.save(event);

        queuePublisherService.publishReviewRequested(event);

        log.info("Queued review for {}#{} (action={}, eventId={})",
                event.getRepoFullName(), event.getPrNumber(), event.getAction(), event.getId());

        return ResponseEntity.ok("queued");
    }
}
