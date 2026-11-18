package com.seatlock.notifications;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class Outbox {
    private final JdbcTemplate jdbc;
    public Outbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void enqueue(UUID account, String subject, UUID reservation) {
        jdbc.update("""
            insert into outbox(id,recipient,subject,body)
            select ?,email,?,? from accounts where id=?
            """, UUID.randomUUID(),subject,subject + " for reservation " + reservation + ".",account);
    }
}
