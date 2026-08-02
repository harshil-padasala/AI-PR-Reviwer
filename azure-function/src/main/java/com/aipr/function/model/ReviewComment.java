package com.aipr.function.model;

/**
 * One line-level review comment as returned by the LLM.
 * Matches the strict JSON schema we force the model to output — see
 * AiReviewClient's system prompt.
 */
public class ReviewComment {

    private String file;
    private int line;
    private String severity; // "high" | "medium" | "low" | "nit"
    private String comment;

    public ReviewComment() {
    }

    public ReviewComment(String file, int line, String severity, String comment) {
        this.file = file;
        this.line = line;
        this.severity = severity;
        this.comment = comment;
    }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
