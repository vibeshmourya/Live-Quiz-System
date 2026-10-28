package com.quizlive.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        body.put("error", message);
        body.put("message", message);
        body.put("status", ex.getStatusCode().value());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        FieldError firstError = ex.getBindingResult().getFieldError();
        String message = firstError != null ? firstError.getDefaultMessage() : "Validation failed";
        body.put("error", message);
        body.put("message", message);
        body.put("status", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<Map<String, Object>> handleDatabaseException(Exception ex) {
        log.error("Database connection or query failure: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Database operation failed. Please verify database connectivity and credentials.");
        body.put("message", "Database operation failed. Please verify database connectivity and credentials.");
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        String message = ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred";
        body.put("error", message);
        body.put("message", message);
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
