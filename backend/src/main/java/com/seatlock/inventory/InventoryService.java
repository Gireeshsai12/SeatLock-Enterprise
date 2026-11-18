package com.seatlock.inventory;

import com.seatlock.seats.Seat;
import com.seatlock.shared.ApiError;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryService {
    private final JdbcTemplate jdbc;
    public InventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> availability(long event) {
        return jdbc.queryForList("""
            select row_number,seat_number,case when reservation_id is null then 'AVAILABLE'
              when held_until is null then 'BOOKED'
              when held_until <= clock_timestamp() then 'AVAILABLE'
              else 'HELD' end as state
            from event_seats where event_id=? order by row_number,seat_number
            """, event);
    }

    public void requireAvailable(long event, Seat seat) {
        record Assignment(UUID reservation, OffsetDateTime deadline) {}
        var rows = jdbc.query("""
            select reservation_id,held_until from event_seats
            where event_id=? and row_number=? and seat_number=? for update
            """, (rs,n) -> new Assignment(rs.getObject(1,UUID.class),rs.getObject(2,OffsetDateTime.class)),
            event,seat.row(),seat.number());
        if (rows.isEmpty()) throw ApiError.missing("Seat");
        var assignment = rows.getFirst();
        if (assignment.reservation() != null && (assignment.deadline() == null ||
                assignment.deadline().isAfter(now()))) {
            throw ApiError.conflict("SEAT_UNAVAILABLE", "Another customer has selected a seat in this cart.");
        }
    }

    public OffsetDateTime now() {
        return jdbc.queryForObject("select clock_timestamp()", (rs,n) -> rs.getObject(1,OffsetDateTime.class));
    }

    public void assign(long event, Seat seat, UUID reservation, OffsetDateTime expiresAt) {
        jdbc.update("""
            update event_seats set reservation_id=?,held_until=?
            where event_id=? and row_number=? and seat_number=?
            """,reservation,expiresAt,event,seat.row(),seat.number());
    }

    public void lockAssigned(UUID reservation) {
        jdbc.queryForList("""
            select seat_number from event_seats where reservation_id=?
            order by row_number,seat_number for update
            """, Integer.class, reservation);
    }

    public int confirm(UUID reservation) {
        return jdbc.update("update event_seats set held_until=null where reservation_id=?",reservation);
    }

    public void release(UUID reservation) {
        jdbc.update("update event_seats set reservation_id=null,held_until=null where reservation_id=?",reservation);
    }

    public int reconcile() {
        return jdbc.update("""
            with expired as (
              select event_id,row_number,seat_number from event_seats
              where held_until is not null and held_until <= clock_timestamp()
              order by event_id,row_number,seat_number for update skip locked
            )
            update event_seats as seat set reservation_id=null,held_until=null
            from expired
            where (seat.event_id,seat.row_number,seat.seat_number) =
                  (expired.event_id,expired.row_number,expired.seat_number)
            """);
    }
}
