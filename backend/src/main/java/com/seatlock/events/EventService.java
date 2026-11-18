package com.seatlock.events;

import com.seatlock.audit.AuditLog;
import com.seatlock.shared.*;
import com.seatlock.venues.VenueService;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    public record Details(@Positive long venueId, @NotBlank @Size(max=120) String title,
        @NotNull Event.Category category, @NotNull @Future OffsetDateTime startsAt,
        @Min(100) @Max(10_000_000) int priceCents, @PositiveOrZero Long version) {}
    private final EventRepository events;
    private final JdbcTemplate jdbc;
    private final VenueService venues;
    private final AuditLog audit;

    public EventService(EventRepository events, JdbcTemplate jdbc, VenueService venues, AuditLog audit) {
        this.events = events;
        this.jdbc = jdbc;
        this.venues = venues;
        this.audit = audit;
    }

    @Transactional
    public Event create(UUID actor, Details details) {
        venues.lock(details.venueId());
        Event event = events.saveAndFlush(new Event(details.venueId(), details));
        initializeSeats(event);
        audit.record(actor, "EVENT_CREATED", event.getId());
        return event;
    }

    @Transactional
    public Event update(UUID actor, long id, Details details) {
        Event event = get(id);
        if (details.version() == null || details.version() != event.getVersion()) {
            throw ApiError.conflict("STALE_VERSION", "Reload the event before editing it.");
        }
        if (details.venueId() != event.getVenueId()) {
            throw new ApiError(HttpStatus.BAD_REQUEST, "IMMUTABLE_VENUE", "Create a new event to change the venue.");
        }
        event.update(details);
        events.flush();
        audit.record(actor, "EVENT_UPDATED", id);
        return event;
    }

    public Event get(long id) {
        return events.findById(id).orElseThrow(() -> ApiError.missing("Event"));
    }

    public List<Map<String,Object>> list(String query, Event.Category category, String sort, PageWindow page) {
        if (query.length() > 120) throw new ApiError(HttpStatus.BAD_REQUEST, "QUERY_TOO_LONG", "Search supports up to 120 characters.");
        String order = switch (sort) {
            case "upcoming" -> "e.starts_at,e.id";
            case "price" -> "e.price_cents,e.id";
            case "title" -> "lower(e.title),e.id";
            default -> throw new ApiError(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Use upcoming, price, or title.");
        };
        String categoryFilter = category == null ? "" : " and e.category=?";
        var args = new ArrayList<Object>();
        args.add(query.strip());
        if (category != null) args.add(category.name());
        args.add(page.size());
        args.add(page.offset());
        return jdbc.queryForList("""
            select e.id,e.title,e.category,e.starts_at,e.price_cents,e.venue_id,e.version,
                   v.name as venue,v.city
            from events e join venues v on e.venue_id=v.id
            where e.starts_at > now() and strpos(lower(e.title || ' ' || v.city), lower(?)) > 0
            """ + categoryFilter + " order by " + order + " limit ? offset ?", args.toArray());
    }

    private void initializeSeats(Event event) {
        jdbc.update("""
            insert into event_seats(event_id,row_number,seat_number)
            select ?,r,s from venues v cross join lateral generate_series(1,v.rows_count) r
            cross join lateral generate_series(1,v.seats_per_row) s where v.id=?
            """, event.getId(), event.getVenueId());
    }
}
