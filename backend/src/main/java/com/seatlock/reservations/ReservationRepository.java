package com.seatlock.reservations;

import com.seatlock.seats.Seat;
import com.seatlock.shared.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationRepository {
    public enum State { HELD, EXPIRED, CONFIRMED, PAYMENT_FAILED, CANCELLED }
    public record Reservation(UUID id, UUID accountId, long eventId, State status,
                              OffsetDateTime expiresAt, int totalCents) {}

    private final JdbcTemplate jdbc;
    public ReservationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Reservation create(UUID account, long event, OffsetDateTime expiry, int price, List<Seat> seats) {
        UUID id = UUID.randomUUID();
        int total = Math.multiplyExact(price, seats.size());
        jdbc.update("insert into reservations(id,account_id,event_id,status,expires_at,total_cents) values (?,?,?,'HELD',?,?)",
            id,account,event,expiry,total);
        for (Seat seat : seats) {
            jdbc.update("insert into reservation_items values (?,?,?,?)",id,seat.row(),seat.number(),price);
        }
        return new Reservation(id,account,event,State.HELD,expiry,total);
    }

    public Reservation lock(UUID account, UUID id) {
        return jdbc.query("select * from reservations where id=? and account_id=? for update",
            (rs,n) -> new Reservation(rs.getObject("id",UUID.class),account,rs.getLong("event_id"),
                State.valueOf(rs.getString("status")),rs.getObject("expires_at",OffsetDateTime.class),
                rs.getInt("total_cents")),id,account).stream().findFirst().orElseThrow(() -> ApiError.missing("Reservation"));
    }

    public Reservation change(Reservation reservation, State state) {
        jdbc.update("update reservations set status=? where id=?",state.name(),reservation.id());
        return new Reservation(reservation.id(),reservation.accountId(),reservation.eventId(),state,
            reservation.expiresAt(),reservation.totalCents());
    }

    public int size(UUID reservation) {
        return jdbc.queryForObject("select count(*) from reservation_items where reservation_id=?", Integer.class,reservation);
    }

    public List<Map<String,Object>> history(UUID account, PageWindow page) {
        return jdbc.query("""
            select r.id,r.event_id,e.title,r.created_at,r.expires_at,r.total_cents,
              case when r.status='HELD' and r.expires_at<=clock_timestamp() then 'EXPIRED' else r.status end as status,
              p.status as payment_status,
              (select string_agg(chr(64+i.row_number)||i.seat_number::text, ', ' order by i.row_number,i.seat_number)
               from reservation_items i where i.reservation_id=r.id) as seats
            from reservations r join events e on e.id=r.event_id
            left join payments p on p.reservation_id=r.id
            where r.account_id=? order by r.created_at desc,r.id limit ? offset ?
            """, new org.springframework.jdbc.core.ColumnMapRowMapper(),account,page.size(),page.offset());
    }
}
