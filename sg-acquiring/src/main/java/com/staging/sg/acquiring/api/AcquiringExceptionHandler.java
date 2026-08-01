package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.port.ServerPosProvisioningException;
import com.staging.sg.acquiring.port.EcommerceNetworkException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AcquiringExceptionHandler {
    @ExceptionHandler({ServerPosProvisioningException.class,
            EcommerceNetworkException.class})
    public ResponseEntity<Map<String, String>> serviceUnavailable(
            RuntimeException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", error.getMessage()));
    }
}
