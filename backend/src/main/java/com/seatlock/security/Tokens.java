package com.seatlock.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.seatlock.users.Accounts.Account;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class Tokens {
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public Tokens(@Value("${seatlock.jwt-secret}") String secret, @Value("${seatlock.demo}") boolean demo) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length == 0 && demo) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        }
        if (key.length < 32) throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes.");
        var hmac = new SecretKeySpec(key, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(hmac));
        decoder = NimbusJwtDecoder.withSecretKey(hmac).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("seatlock"));
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public String issue(Account account) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("seatlock").subject(account.id().toString())
            .issuedAt(now).expiresAt(now.plusSeconds(3600))
            .claim("roles", java.util.List.of(account.role())).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
