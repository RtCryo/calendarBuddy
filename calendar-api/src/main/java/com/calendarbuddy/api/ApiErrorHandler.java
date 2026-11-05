package com.calendarbuddy.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException error) {
        String reason = error.getReason() == null ? error.getStatusCode().toString() : error.getReason();
        Map<String, String> fields = Map.of();
        int separator = reason.indexOf(':');
        if (separator > 0 && error.getStatusCode().is4xxClientError()) {
            fields = Map.of(reason.substring(0, separator), reason.substring(separator + 1).trim());
        }
        return ResponseEntity.status(error.getStatusCode()).body(Map.of(
                "message", reason, "fieldErrors", fields));
    }
}
