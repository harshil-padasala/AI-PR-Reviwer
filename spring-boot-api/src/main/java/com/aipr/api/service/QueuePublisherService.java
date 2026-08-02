package com.aipr.api.service;

import com.aipr.api.model.PullRequestEvent;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes a lightweight "review requested" event onto an Azure Service
 * Bus queue. The Azure Function is queue-triggered and picks this up
 * asynchronously, so the webhook can return 200 to GitHub immediately
 * instead of blocking on the (slow, potentially rate-limited) LLM call.
 */
@Service
public class QueuePublisherService {

    private static final Logger log = LoggerFactory.getLogger(QueuePublisherService.class);

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueuePublisherService(
            @Value("${azure.servicebus.connection-string}") String connectionString,
            @Value("${azure.servicebus.queue-name}") String queueName) {
        this.senderClient = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName)
                .buildClient();
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
            senderClient.sendMessage(new ServiceBusMessage(json));

            log.info("Published review-requested event for {}#{} (eventId={})",
                    event.getRepoFullName(), event.getPrNumber(), event.getId());
        } catch (Exception e) {
            log.error("Failed to publish review-requested event for eventId={}", event.getId(), e);
            throw new IllegalStateException("Could not queue PR for review", e);
        }
    }
}
