package com.aipr.function;

import com.aipr.function.model.ReviewComment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the model's raw JSON response into a typed result. Defensive by
 * design: LLMs occasionally wrap JSON in markdown fences or add stray
 * text despite instructions, so we strip fences and validate structure
 * before trusting the content.
 */
public class ReviewParser {

    public static class ParsedReview {
        public final String summary;
        public final List<ReviewComment> comments;

        public ParsedReview(String summary, List<ReviewComment> comments) {
            this.summary = summary;
            this.comments = comments;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public ParsedReview parse(String rawModelOutput) {
        String cleaned = stripMarkdownFences(rawModelOutput);

        try {
            JsonNode root = mapper.readTree(cleaned);
            String summary = root.path("summary").asText("No summary provided.");
            List<ReviewComment> comments = new ArrayList<>();

            JsonNode commentsNode = root.path("comments");
            if (commentsNode.isArray()) {
                for (JsonNode c : commentsNode) {
                    String file = c.path("file").asText(null);
                    int line = c.path("line").asInt(-1);
                    String severity = c.path("severity").asText("medium");
                    String comment = c.path("comment").asText(null);

                    // Skip malformed entries rather than failing the whole review
                    if (file != null && comment != null && line > 0) {
                        comments.add(new ReviewComment(file, line, severity, comment));
                    }
                }
            }
            return new ParsedReview(summary, comments);
        } catch (Exception e) {
            // Fail soft: still record that a review happened, just with no structured comments
            return new ParsedReview(
                    "AI review completed but the response could not be parsed as structured JSON.",
                    List.of());
        }
    }

    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(json)?", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
