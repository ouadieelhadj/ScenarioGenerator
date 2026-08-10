package com.staging.sg.acquiring.integration;
public class Way4ConnectorException extends RuntimeException {
    private final boolean retryable; private final boolean mappingBlocked;
    public Way4ConnectorException(String message, boolean retryable, boolean mappingBlocked, Throwable cause) {
        super(message, cause); this.retryable = retryable; this.mappingBlocked = mappingBlocked;
    }
    public boolean retryable() { return retryable; } public boolean mappingBlocked() { return mappingBlocked; }
}
