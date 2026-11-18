package com.seatlock.shared;

import org.springframework.http.HttpStatus;

public final class ApiError extends RuntimeException {
    public final HttpStatus status;
    public final String code;

    public ApiError(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiError missing(String resource) {
        return new ApiError(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " was not found.");
    }

    public static ApiError conflict(String code, String message) {
        return new ApiError(HttpStatus.CONFLICT, code, message);
    }
}
