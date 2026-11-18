package com.seatlock.reservations;

import com.seatlock.audit.AuditLog;
import com.seatlock.events.EventService;
import com.seatlock.inventory.InventoryService;
import com.seatlock.notifications.Outbox;
import com.seatlock.payments.*;
import com.seatlock.reservations.ReservationRepository.*;
import com.seatlock.seats.Seat;
import com.seatlock.shared.ApiError;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final InventoryService inventory;
    private final EventService events;
    private final PaymentSimulator simulator;
    private final PaymentLedger payments;
    private final Outbox outbox;
    private final AuditLog audit;
    private final int holdSeconds;

    public ReservationService(ReservationRepository reservations, InventoryService inventory, EventService events,
            PaymentSimulator simulator, PaymentLedger payments, Outbox outbox, AuditLog audit,
            @Value("${seatlock.hold-seconds}") int holdSeconds) {
        if (holdSeconds < 1 || holdSeconds > 1800) throw new IllegalArgumentException("HOLD_SECONDS must be 1–1800.");
        this.reservations = reservations;
        this.inventory = inventory;
        this.events = events;
        this.simulator = simulator;
        this.payments = payments;
        this.outbox = outbox;
        this.audit = audit;
        this.holdSeconds = holdSeconds;
    }

    @Transactional
    public Reservation hold(UUID account, long eventId, List<Seat> selected) {
        List<Seat> seats = selected.stream().distinct().sorted().toList();
        if (seats.size() != selected.size()) {
            throw new ApiError(HttpStatus.BAD_REQUEST, "DUPLICATE_SEAT", "Choose each seat at most once.");
        }
        var event = events.get(eventId);
        for (Seat seat : seats) inventory.requireAvailable(eventId, seat);
        var now = inventory.now();
        if (!event.getStartsAt().isAfter(now)) throw ApiError.conflict("EVENT_STARTED","The event has started.");
        var deadline = now.plusSeconds(holdSeconds);
        var reservation = reservations.create(account,eventId,event.getStartsAt().isBefore(deadline) ?
            event.getStartsAt() : deadline,event.getPriceCents(),seats);
        for (Seat seat : seats) inventory.assign(eventId,seat,reservation.id(),reservation.expiresAt());
        audit.record(account,"HOLD_CREATED",reservation.id());
        return reservation;
    }

    @Transactional
    public Reservation checkout(UUID account, UUID id, PaymentSimulator.Scenario scenario) {
        var reservation = reservations.lock(account,id);
        if (reservation.status() == State.CONFIRMED || reservation.status() == State.PAYMENT_FAILED) return reservation;
        if (reservation.status() != State.HELD) throw ApiError.conflict("INVALID_STATE","This reservation cannot be checked out.");
        inventory.lockAssigned(id);
        if (!reservation.expiresAt().isAfter(inventory.now())) {
            throw ApiError.conflict("HOLD_EXPIRED","Your cart has expired. Select seats again.");
        }
        if (!events.get(reservation.eventId()).getStartsAt().isAfter(inventory.now())) {
            throw ApiError.conflict("EVENT_STARTED","The event has started.");
        }
        var outcome = simulator.authorize(scenario);
        payments.record(id,outcome,reservation.totalCents());
        if (outcome == PaymentSimulator.Result.DECLINED) {
            inventory.release(id);
            audit.record(account,"PAYMENT_DECLINED",id);
            return reservations.change(reservation,State.PAYMENT_FAILED);
        }
        if (inventory.confirm(id) != reservations.size(id)) {
            throw ApiError.conflict("SEAT_UNAVAILABLE","The original seats are no longer assigned.");
        }
        outbox.enqueue(account,"Reservation confirmed",id);
        audit.record(account,"RESERVATION_CONFIRMED",id);
        return reservations.change(reservation,State.CONFIRMED);
    }

    @Transactional
    public Reservation cancel(UUID account, UUID id) {
        var reservation = reservations.lock(account,id);
        if (reservation.status() == State.CANCELLED) return reservation;
        if (reservation.status() == State.PAYMENT_FAILED) {
            throw ApiError.conflict("INVALID_STATE","A declined payment cannot be refunded.");
        }
        inventory.lockAssigned(id);
        if (reservation.status() == State.CONFIRMED) {
            if (!events.get(reservation.eventId()).getStartsAt().isAfter(inventory.now())) {
                throw ApiError.conflict("EVENT_STARTED","Confirmed reservations can only be cancelled before the event.");
            }
            payments.refund(id);
            outbox.enqueue(account,"Reservation cancelled and demo refund recorded",id);
        }
        inventory.release(id);
        audit.record(account,"RESERVATION_CANCELLED",id);
        return reservations.change(reservation,State.CANCELLED);
    }
}
