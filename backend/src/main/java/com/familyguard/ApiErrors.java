package com.familyguard;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", Objects.requireNonNullElse(e.getReason(), "Request rejected")));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", "Invalid request. Check fields and permitted ranges."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("message", "Concurrent change. Refresh and retry."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    String reference = UUID.randomUUID().toString();
    org.slf4j.LoggerFactory.getLogger(ApiErrors.class)
        .error("Request failed, reference {}", reference, e);
    return ResponseEntity.internalServerError()
        .body(
            Map.of(
                "message",
                "The server could not complete the request. Retry or quote reference "
                    + reference));
  }
}
