package com.aipr.api.service;

import com.aipr.api.model.PullRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes a lightweight "review requested" event onto an Amazon SQS queue.
 * The AWS Lambda worker is SQS-triggered and picks this up asynchronously,
 * so the webhook can return 200 to GitHub immediately instead of blocking on
 * the (slow, potentially rate-limited) LLM call.
 */
@Service
public class QueuePublisherService {

    private static final Logger log = LoggerFactory.getLogger(QueuePublisherService.class);

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueuePublisherService(
            @Value("${aws.sqs.queue-url}") String queueUrl,
            @Value("${aws.region:us-east-1}") String region) {
        this.queueUrl = queueUrl;
        this.sqsClient = SqsClient.builder()
                .region(Region.of(region))
                .build();
    }

    public void publishReviewRequested(PullRequestEvent event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("pullRequestEventId", event.getId());
            message.put("repoFullName", event.getRepoFullName());
            message.put("prNumber", event.getPrNumber());
            message.put("headSha", event.getHeadSha());
            message.put("diffUrl", event.getDiffUrl());

            String json = objectMapper.writeValueAsString(message);

            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(json)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);

            log.info("Published review-requested event for {}#{} (eventId={})",
                    event.getRepoFullName(), event.getPrNumber(), event.getId());
        } catch (Exception e) {
            log.error("Failed to publish review-requested event for eventId={}", event.getId(), e);
            throw new IllegalStateException("Could not queue PR for review", e);
        }
    }
}
