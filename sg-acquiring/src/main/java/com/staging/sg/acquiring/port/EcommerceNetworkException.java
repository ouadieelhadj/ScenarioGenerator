package com.staging.sg.acquiring.port;

public class EcommerceNetworkException extends RuntimeException {
    public EcommerceNetworkException(String message) { super(message); }
    public EcommerceNetworkException(String message, Throwable cause) { super(message, cause); }
}
