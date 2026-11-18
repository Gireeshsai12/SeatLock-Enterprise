package com.seatlock.seats;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record Seat(@Min(1) @Max(26) int row, @Min(1) @Max(30) int number) implements Comparable<Seat> {
    @Override
    public int compareTo(Seat other) {
        int byRow = Integer.compare(row, other.row);
        return byRow == 0 ? Integer.compare(number, other.number) : byRow;
    }
}
