package com.seatlock.reservations;

import com.seatlock.payments.PaymentSimulator;
import com.seatlock.reservations.ReservationRepository.Reservation;
import com.seatlock.seats.Seat;
import com.seatlock.shared.PageWindow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    public record Cart(@Positive long eventId, @NotEmpty @Size(max=8) List<@NotNull @Valid Seat> seats) {}
    public record Checkout(@NotNull PaymentSimulator.Scenario scenario) {}
    private final ReservationService service;
    private final ReservationRepository reservations;

    public ReservationController(ReservationService service, ReservationRepository reservations) {
        this.service = service;
        this.reservations = reservations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Reservation hold(JwtAuthenticationToken principal, @Valid @RequestBody Cart cart) {
        return service.hold(account(principal),cart.eventId(),cart.seats());
    }

    @PostMapping("/{id}/checkout")
    Reservation checkout(JwtAuthenticationToken principal, @PathVariable UUID id, @Valid @RequestBody Checkout checkout) {
        return service.checkout(account(principal),id,checkout.scenario());
    }

    @PostMapping("/{id}/cancel")
    Reservation cancel(JwtAuthenticationToken principal, @PathVariable UUID id) {
        return service.cancel(account(principal),id);
    }

    @GetMapping
    List<Map<String,Object>> history(JwtAuthenticationToken principal,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return reservations.history(account(principal),new PageWindow(page,size));
    }

    private UUID account(JwtAuthenticationToken principal) {
        return UUID.fromString(principal.getName());
    }
}
