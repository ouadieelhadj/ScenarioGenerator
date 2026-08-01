package com.staging.sg.acquiring.port;

public class ServerPosProvisioningException extends RuntimeException {
    public ServerPosProvisioningException(String message) {
        super(message);
    }

    public ServerPosProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
