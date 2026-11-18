package com.seatlock.payments;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentLedger {
    private final JdbcTemplate jdbc;
    public PaymentLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID id, PaymentSimulator.Result result, int cents) {
        jdbc.update("insert into payments(reservation_id,status,amount_cents) values (?,?,?)", id,result.name(),cents);
    }

    public void refund(UUID id) {
        int rows = jdbc.update("""
            update payments set status='REFUNDED',updated_at=clock_timestamp()
            where reservation_id=? and status='PAID'
            """, id);
        if (rows != 1) throw new IllegalStateException("Confirmed reservation has no paid ledger entry.");
    }
}
