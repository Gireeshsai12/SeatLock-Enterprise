package com.seatlock.identity;

import com.seatlock.security.Tokens;
import com.seatlock.security.AuthRateLimit;
import jakarta.servlet.http.HttpServletRequest;
import com.seatlock.users.Accounts.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityController {
    public record Credentials(@NotBlank @Email @Size(max=254) String email,
                              @NotBlank @Size(min=10,max=72) String password) {}
    public record Session(String accessToken, String email, String role) {}
    public record Options(boolean demo, boolean google) {}
    private final IdentityService identity;
    private final Tokens tokens;
    private final Options options;
    private final AuthRateLimit limiter;

    public IdentityController(IdentityService identity, Tokens tokens, AuthRateLimit limiter,
            @Value("${seatlock.demo}") boolean demo, ObjectProvider<ClientRegistrationRepository> registrations) {
        this.identity = identity;
        this.tokens = tokens;
        this.limiter = limiter;
        var clients = registrations.getIfAvailable();
        this.options = new Options(demo, clients != null && clients.findByRegistrationId("google") != null);
    }

    @GetMapping("/options")
    Options options() { return options; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Session register(@Valid @RequestBody Credentials credentials, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        return session(identity.register(credentials.email(), credentials.password()));
    }

    @PostMapping("/login")
    Session login(@Valid @RequestBody Credentials credentials, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        return session(identity.login(credentials.email(), credentials.password()));
    }

    private Session session(Account account) {
        return new Session(tokens.issue(account), account.email(), account.role());
    }
}
