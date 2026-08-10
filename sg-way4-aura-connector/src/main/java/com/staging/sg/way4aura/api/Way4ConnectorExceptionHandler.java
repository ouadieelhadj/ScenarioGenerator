package com.staging.sg.way4aura.api;

import com.staging.sg.way4aura.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Way4ConnectorExceptionHandler {
    @ExceptionHandler(AuraMappingBlockedException.class)
    ResponseEntity<ErrorView> mapping(AuraMappingBlockedException exception) {
        return ResponseEntity.unprocessableEntity().body(new ErrorView("MAPPING_BLOCKED", exception.getMessage()));
    }
    @ExceptionHandler(Way4XsdRejectedException.class)
    ResponseEntity<ErrorView> xsd(Way4XsdRejectedException exception) {
        return ResponseEntity.unprocessableEntity().body(new ErrorView("XSD_REJECTED", exception.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorView> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(new ErrorView("INVALID_REQUEST", exception.getMessage()));
    }
    public record ErrorView(String code, String message) {}
}
