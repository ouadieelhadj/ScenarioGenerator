package com.staging.sg.onboarding.port;

public class Way4ConnectorTransportException extends RuntimeException {
    private final boolean retryable;
    public Way4ConnectorTransportException(String message, boolean retryable, Throwable cause) {
        super(message, cause); this.retryable = retryable;
    }
    public boolean retryable() { return retryable; }
}
