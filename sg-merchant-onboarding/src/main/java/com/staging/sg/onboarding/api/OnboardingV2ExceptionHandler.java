package com.staging.sg.onboarding.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        MerchantOnboardingV2Controller.class, OnboardingReferenceController.class
})
public class OnboardingV2ExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(error(exception.getMessage(), "VALIDATION"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error(exception.getMessage(), "CONFLICT"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        var fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst();
        String field = fieldError.map(error -> error.getField()).orElse("request");
        String message = fieldError.map(error -> error.getDefaultMessage()).orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message,
                "code", "VALIDATION", "field", field));
    }

    private static Map<String, String> error(String message, String fallbackCode) {
        String safe = message == null ? "Request failed" : message;
        int separator = safe.indexOf(':');
        String candidate = separator > 0 ? safe.substring(0, separator) : fallbackCode;
        String code = candidate.matches("[A-Z]+-[0-9]{3}|CONCURRENCY|MIG-[0-9]{3}")
                ? candidate : fallbackCode;
        String detail = separator > 0 ? safe.substring(separator + 1).trim() : safe;
        String field = detail.contains(" ") ? detail.substring(0, detail.indexOf(' ')) : "request";
        return Map.of("error", detail, "code", code, "field", field);
    }
}
