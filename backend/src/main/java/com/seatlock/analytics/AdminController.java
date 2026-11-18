package com.seatlock.analytics;

import com.seatlock.audit.AuditLog;
import com.seatlock.events.*;
import com.seatlock.inventory.InventoryService;
import com.seatlock.shared.PageWindow;
import com.seatlock.venues.VenueService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final VenueService venues;
    private final EventService events;
    private final InventoryService inventory;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;

    public AdminController(VenueService venues, EventService events, InventoryService inventory,
            AuditLog audit, JdbcTemplate jdbc) {
        this.venues = venues;
        this.events = events;
        this.inventory = inventory;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @PostMapping("/venues")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Long> createVenue(JwtAuthenticationToken actor, @Valid @RequestBody VenueService.Layout layout) {
        return Map.of("id",venues.create(id(actor),layout));
    }

    @PutMapping("/venues/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editVenue(JwtAuthenticationToken actor, @PathVariable long id, @Valid @RequestBody VenueService.Layout layout) {
        venues.update(id(actor),id,layout);
    }

    @GetMapping("/venues")
    List<Map<String,Object>> venues(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return venues.list(new PageWindow(page,size));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    Event createEvent(JwtAuthenticationToken actor, @Valid @RequestBody EventService.Details details) {
        return events.create(id(actor),details);
    }

    @PutMapping("/events/{id}")
    Event editEvent(JwtAuthenticationToken actor, @PathVariable long id, @Valid @RequestBody EventService.Details details) {
        return events.update(id(actor),id,details);
    }

    @GetMapping("/analytics")
    Map<String,Object> analytics() {
        return jdbc.queryForMap("""
            select (select count(*) from events where starts_at>now()) as upcoming,
            (select count(*) from reservations where status='CONFIRMED') as confirmed,
            (select coalesce(sum(amount_cents),0) from payments where status='PAID') as net_cents,
            (select count(*) from event_seats where held_until>clock_timestamp()) as active_holds,
            (select count(*) from outbox where sent_at is null and dead_at is null) as outbox_pending,
            (select count(*) from outbox where dead_at is not null) as outbox_dead,
            (select count(*) from notification_deliveries) as notifications_delivered
            """);
    }

    @GetMapping("/audit")
    List<Map<String,Object>> audit(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return audit.list(new PageWindow(page,size));
    }

    @GetMapping("/notifications")
    List<Map<String,Object>> notifications(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        var window = new PageWindow(page,size);
        return jdbc.queryForList("""
            select id,subject,delivery_mode,delivered_at from notification_deliveries
            order by delivered_at desc,id limit ? offset ?
            """,window.size(),window.offset());
    }

    @PostMapping("/inventory/reconcile")
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Integer> reconcile(JwtAuthenticationToken actor) {
        int released = inventory.reconcile();
        audit.record(id(actor),"INVENTORY_RECONCILED",released);
        return Map.of("released",released);
    }

    private UUID id(JwtAuthenticationToken principal) { return UUID.fromString(principal.getName()); }
}
