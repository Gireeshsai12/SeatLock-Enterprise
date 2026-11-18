package com.seatlock.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiError.class)
    public ResponseEntity<Object> api(ApiError error) {
        return problem(error.status, error.code, error.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> stale() {
        return problem(HttpStatus.CONFLICT, "STALE_VERSION", "Reload the event and retry.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error, HttpHeaders headers, HttpStatusCode status,
            org.springframework.web.context.request.WebRequest request) {
        Map<String,String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        var body = ProblemDetail.forStatusAndDetail(status, "Some fields need attention.");
        body.setProperty("code", "VALIDATION_FAILED");
        body.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String code, String detail) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
