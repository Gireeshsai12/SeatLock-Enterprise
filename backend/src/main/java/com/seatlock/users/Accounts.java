package com.seatlock.users;

import com.seatlock.shared.ApiError;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class Accounts {
    public record Account(UUID id, String email, String passwordHash, String role) {}
    private final JdbcTemplate jdbc;

    public Accounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> byEmail(String email) {
        return find("email", email);
    }

    public Optional<Account> byGoogleSubject(String subject) {
        return find("google_subject", subject);
    }

    private Optional<Account> find(String column, String value) {
        return jdbc.query("select id,email,password_hash,role from accounts where " + column + "=?",
            (row, n) -> new Account(row.getObject("id", UUID.class), row.getString("email"),
                row.getString("password_hash"), row.getString("role")), value).stream().findFirst();
    }

    public Account create(String email, String passwordHash, String role, String googleSubject) {
        var account = new Account(UUID.randomUUID(), email, passwordHash, role);
        int inserted = jdbc.update("""
            insert into accounts(id,email,password_hash,role,google_subject)
            values (?,?,?,?,?) on conflict do nothing
            """, account.id(), email, passwordHash, role, googleSubject);
        if (inserted == 0) throw ApiError.conflict("ACCOUNT_EXISTS", "An account already exists for these credentials.");
        return account;
    }
}
