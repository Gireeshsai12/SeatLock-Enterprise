ANALYZE events;
ANALYZE event_seats;
EXPLAIN (ANALYZE, BUFFERS)
SELECT id,title,starts_at FROM events
WHERE category='CONCERT' AND starts_at>now() ORDER BY starts_at,id LIMIT 12;
EXPLAIN (ANALYZE, BUFFERS)
SELECT row_number,seat_number,reservation_id,held_until FROM event_seats
WHERE event_id=1 ORDER BY row_number,seat_number;
