package com.seatlock.shared;

import com.seatlock.events.*;
import com.seatlock.users.Accounts;
import com.seatlock.venues.VenueService;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="seatlock.worker",havingValue="false",matchIfMissing=true)
public class DemoBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final EventService events;
    private final VenueService venues;
    private final Accounts accounts;
    private final PasswordEncoder encoder;
    private final String adminPassword;
    private final boolean demo;

    public DemoBootstrap(JdbcTemplate jdbc, EventService events, VenueService venues, Accounts accounts,
            PasswordEncoder encoder, @Value("${ADMIN_PASSWORD:}") String adminPassword,
            @Value("${seatlock.demo}") boolean demo) {
        this.jdbc=jdbc;
        this.events=events;
        this.venues=venues;
        this.accounts=accounts;
        this.encoder=encoder;
        this.adminPassword=adminPassword;
        this.demo=demo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.execute("select pg_advisory_xact_lock(812405)");
        if (!adminPassword.isBlank()) {
            var existing = accounts.byEmail("admin@seatlock.local");
            if (existing.isEmpty()) {
                if (adminPassword.length() < 12 || adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                    throw new IllegalArgumentException("ADMIN_PASSWORD must be at least 12 characters and at most 72 UTF-8 bytes.");
                }
                accounts.create("admin@seatlock.local",encoder.encode(adminPassword),"ADMIN",null);
            } else if (!existing.get().role().equals("ADMIN")) {
                throw new IllegalStateException("Reserved admin email belongs to a customer.");
            }
        }
        if (!demo || Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from events)", Boolean.class))) return;
        long theater = venues.create(null,new VenueService.Layout("Tremont Hall","Boston",6,10));
        long arena = venues.create(null,new VenueService.Layout("Canal Arena","Lowell",8,12));
        String[] titles = {"Midnight Jazz Sessions","The Last Frame","Lowell City Finals","Northeast Tech Summit","A Night of Contemporary Dance"};
        var categories = Event.Category.values();
        for (int i=0;i<titles.length;i++) {
            events.create(null,new EventService.Details(i == 2 ? arena : theater,titles[i],categories[i],
                OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(14L + i * 7).withHour(23).withMinute(0).withSecond(0).withNano(0),
                2400 + i * 900,null));
        }
    }
}
