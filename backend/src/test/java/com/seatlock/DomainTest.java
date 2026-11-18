package com.seatlock;

import com.seatlock.payments.PaymentSimulator;
import com.seatlock.seats.Seat;
import com.seatlock.shared.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    @Test
    void locksInRowThenSeatOrder() {
        assertEquals(List.of(new Seat(1,2),new Seat(1,10),new Seat(2,1)),
            List.of(new Seat(2,1),new Seat(1,10),new Seat(1,2)).stream().sorted().toList());
    }

    @Test
    void validatesPagination() {
        assertEquals(30,new PageWindow(3,10).offset());
        assertThrows(ApiError.class,() -> new PageWindow(-1,10));
        assertThrows(ApiError.class,() -> new PageWindow(0,101));
    }

    @Test
    void simulatorHasDeterministicOutcomes() {
        var simulator = new PaymentSimulator();
        assertEquals(PaymentSimulator.Result.PAID,simulator.authorize(PaymentSimulator.Scenario.APPROVE));
        assertEquals(PaymentSimulator.Result.DECLINED,simulator.authorize(PaymentSimulator.Scenario.DECLINE));
    }
}
