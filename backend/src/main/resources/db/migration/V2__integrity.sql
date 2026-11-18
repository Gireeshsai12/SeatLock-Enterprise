alter table event_seats add constraint assigned_hold check (reservation_id is not null or held_until is null);
create unique index reservations_event_id_idx on reservations(event_id,id);
alter table event_seats add constraint seat_assignment_event
 foreign key (event_id,reservation_id) references reservations(event_id,id);
