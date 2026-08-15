package com.staging.sg.fraud.api;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class FraudExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class})
    ResponseEntity<Map<String,Object>> badRequest(Exception e){return response(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",e.getMessage());}
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String,Object>> notFound(Exception e){return response(HttpStatus.NOT_FOUND,"NOT_FOUND",e.getMessage());}
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String,Object>> forbidden(Exception e){return response(HttpStatus.FORBIDDEN,"MEMBER_CONTEXT_REQUIRED",e.getMessage());}
    private ResponseEntity<Map<String,Object>> response(HttpStatus status,String code,String message){return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now().toString(),"code",code,"message",String.valueOf(message)));}
}
