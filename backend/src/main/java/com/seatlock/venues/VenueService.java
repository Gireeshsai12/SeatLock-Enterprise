package com.seatlock.venues;

import com.seatlock.audit.AuditLog;
import com.seatlock.shared.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {
    public record Layout(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=120) String city,
                         @Min(1) @Max(26) int rows, @Min(1) @Max(30) int seatsPerRow) {}
    private final JdbcTemplate jdbc;
    private final AuditLog audit;

    public VenueService(JdbcTemplate jdbc, AuditLog audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public long create(UUID actor, Layout layout) {
        long id = jdbc.queryForObject("""
            insert into venues(name,city,rows_count,seats_per_row) values (?,?,?,?) returning id
            """, Long.class, layout.name().strip(), layout.city().strip(), layout.rows(), layout.seatsPerRow());
        audit.record(actor, "VENUE_CREATED", id);
        return id;
    }

    @Transactional
    public void update(UUID actor, long id, Layout layout) {
        lock(id);
        int count = jdbc.update("""
            update venues set name=?,city=?,rows_count=?,seats_per_row=? where id=?
            and ((rows_count=? and seats_per_row=?) or not exists (select 1 from events where venue_id=?))
            """, layout.name().strip(), layout.city().strip(), layout.rows(), layout.seatsPerRow(), id,
            layout.rows(), layout.seatsPerRow(), id);
        if (count == 0) throw ApiError.conflict("VENUE_IN_USE", "Create a new layout for future events.");
        audit.record(actor, "VENUE_UPDATED", id);
    }

    public void lock(long id) {
        if (jdbc.queryForList("select id from venues where id=? for update", Long.class, id).isEmpty()) {
            throw ApiError.missing("Venue");
        }
    }

    public List<Map<String,Object>> list(PageWindow page) {
        return jdbc.queryForList("select * from venues order by name,id limit ? offset ?", page.size(), page.offset());
    }
}
