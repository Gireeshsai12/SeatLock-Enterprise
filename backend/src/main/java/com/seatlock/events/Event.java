package com.seatlock.events;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name="events")
public class Event {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="venue_id") private long venueId;
    private String title;
    @Enumerated(EnumType.STRING) private Category category;
    @Column(name="starts_at") private OffsetDateTime startsAt;
    @Column(name="price_cents") private int priceCents;
    @Version private long version;

    public enum Category { CONCERT, MOVIE, SPORTS, CONFERENCE, PERFORMANCE }

    protected Event() {}

    Event(long venueId, EventService.Details details) {
        this.venueId = venueId;
        update(details);
    }

    void update(EventService.Details details) {
        title = details.title().strip();
        category = details.category();
        startsAt = details.startsAt();
        priceCents = details.priceCents();
    }

    public Long getId() { return id; }
    public long getVenueId() { return venueId; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public int getPriceCents() { return priceCents; }
    public long getVersion() { return version; }
}
