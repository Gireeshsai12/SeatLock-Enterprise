package com.seatlock.identity;

import com.seatlock.shared.ApiError;
import com.seatlock.users.Accounts;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    private final Accounts accounts;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public IdentityService(Accounts accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    public Accounts.Account register(String email, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ApiError(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Password must fit within 72 UTF-8 bytes.");
        }
        return accounts.create(customerEmail(email), encoder.encode(password), "CUSTOMER", null);
    }

    public Accounts.Account login(String email, String password) {
        var account = accounts.byEmail(normalize(email)).orElse(null);
        var hash = account == null || account.passwordHash() == null ? dummyHash : account.passwordHash();
        boolean valid = password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
            && encoder.matches(password, hash);
        if (!valid || account == null || account.passwordHash() == null) {
            throw new ApiError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        return account;
    }

    public Accounts.Account google(String subject, String email) {
        return accounts.byGoogleSubject(subject).orElseGet(
            () -> accounts.create(customerEmail(email), null, "CUSTOMER", subject));
    }

    private String customerEmail(String email) {
        String normalized = normalize(email);
        if ("admin@seatlock.local".equals(normalized)) {
            throw new ApiError(HttpStatus.BAD_REQUEST, "RESERVED_EMAIL", "Choose a different email address.");
        }
        return normalized;
    }

    private String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
