package com.confluence.ingestor.confluence;

/**
 * Raised for Confluence HTTP or parsing errors. Never include PAT in messages.
 */
public class ConfluenceClientError extends RuntimeException {

    private final Integer statusCode;

    public ConfluenceClientError(String message) {
        super(message);
        this.statusCode = null;
    }

    public ConfluenceClientError(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
