package com.staging.sg.onboarding.port;

public class ProvisioningTransportException extends RuntimeException {
    private final boolean retryable;

    public ProvisioningTransportException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() { return retryable; }
}
