package com.seatlock.shared;

import org.springframework.http.HttpStatus;

public record PageWindow(int page, int size) {
    public PageWindow {
        if (page < 0 || page > 10000 || size < 1 || size > 100) {
            throw new ApiError(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page must be 0–10000 and size must be 1–100.");
        }
    }
    public int offset() { return page * size; }
}
