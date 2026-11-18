package com.seatlock;

import com.seatlock.identity.IdentityService;
import com.seatlock.shared.ApiError;
import com.seatlock.users.Accounts;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityServiceTest {
    private final Accounts accounts = mock(Accounts.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final IdentityService identity = new IdentityService(accounts,encoder);

    @Test
    void registrationNormalizesEmailAndAlwaysUsesCustomerRole() {
        when(encoder.encode("local test passphrase")).thenReturn("hashed");
        var expected = new Accounts.Account(UUID.randomUUID(),"buyer@example.test","hashed","CUSTOMER");
        when(accounts.create("buyer@example.test","hashed","CUSTOMER",null)).thenReturn(expected);
        assertEquals(expected,identity.register(" Buyer@Example.Test ","local test passphrase"));
    }

    @Test
    void rejectsReservedAdminEmail() {
        var error = assertThrows(ApiError.class,() -> identity.register("ADMIN@seatlock.local","local test passphrase"));
        assertEquals("RESERVED_EMAIL",error.code);
        verifyNoInteractions(accounts);
    }

    @Test
    void rejectsBadPassword() {
        var account = new Accounts.Account(UUID.randomUUID(),"buyer@example.test","hashed","CUSTOMER");
        when(accounts.byEmail(account.email())).thenReturn(Optional.of(account));
        assertEquals("INVALID_CREDENTIALS", assertThrows(ApiError.class,
            () -> identity.login(account.email(),"wrong password")).code);
    }
}
