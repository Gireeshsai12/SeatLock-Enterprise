package com.seatlock.payments;

import org.springframework.stereotype.Component;

@Component
public class PaymentSimulator {
    public enum Scenario { APPROVE, DECLINE }
    public enum Result { PAID, DECLINED, REFUNDED }

    public Result authorize(Scenario scenario) {
        return switch (scenario) {
            case APPROVE -> Result.PAID;
            case DECLINE -> Result.DECLINED;
        };
    }
}
