package com.aipr.lambda;

import com.aipr.lambda.model.ReviewComment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReviewParserTest {

    @Test
    public void testParseValidJson() {
        ReviewParser parser = new ReviewParser();
        String rawOutput = """
            {
              "summary": "Solid PR overall, couple edge cases.",
              "comments": [
                {
                  "file": "src/Main.java",
                  "line": 42,
                  "severity": "high",
                  "comment": "Potential NullPointerException"
                }
              ]
            }
            """;

        ReviewParser.ParsedReview review = parser.parse(rawOutput);
        assertEquals("Solid PR overall, couple edge cases.", review.summary);
        assertEquals(1, review.comments.size());
        ReviewComment c = review.comments.get(0);
        assertEquals("src/Main.java", c.getFile());
        assertEquals(42, c.getLine());
        assertEquals("high", c.getSeverity());
        assertEquals("Potential NullPointerException", c.getComment());
    }

    @Test
    public void testParseJsonWithMarkdownFences() {
        ReviewParser parser = new ReviewParser();
        String rawOutput = """
            ```json
            {
              "summary": "Looks good.",
              "comments": []
            }
            ```
            """;

        ReviewParser.ParsedReview review = parser.parse(rawOutput);
        assertEquals("Looks good.", review.summary);
        assertTrue(review.comments.isEmpty());
    }

    @Test
    public void testParseInvalidJsonFailSoft() {
        ReviewParser parser = new ReviewParser();
        String rawOutput = "This is not valid JSON output from LLM";

        ReviewParser.ParsedReview review = parser.parse(rawOutput);
        assertNotNull(review.summary);
        assertTrue(review.summary.contains("could not be parsed"));
        assertTrue(review.comments.isEmpty());
    }
}
