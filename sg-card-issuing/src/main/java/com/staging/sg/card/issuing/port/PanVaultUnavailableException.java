package com.staging.sg.card.issuing.port;

public class PanVaultUnavailableException extends RuntimeException {
    public PanVaultUnavailableException() {
        super("PAN vault is not connected");
    }
}
