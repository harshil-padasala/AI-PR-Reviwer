package com.aipr.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

/**
 * Verifies the HMAC-SHA256 signature GitHub attaches to every webhook
 * delivery (the X-Hub-Signature-256 header) against our shared webhook
 * secret. This stops anyone who isn't GitHub from triggering a review.
 * https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries
 */
@Service
public class GitHubWebhookService {

    private static final String HMAC_ALGO = "HmacSHA256";

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    public boolean isValidSignature(String payloadBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String providedSignature = signatureHeader.substring("sha256=".length());

        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] computed = mac.doFinal(payloadBody.getBytes(StandardCharsets.UTF_8));
            String computedHex = bytesToHex(computed);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    providedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to verify GitHub webhook signature", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Only these actions warrant kicking off a new AI review. */
    public boolean isReviewableAction(String action) {
        return "opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action);
    }
}
