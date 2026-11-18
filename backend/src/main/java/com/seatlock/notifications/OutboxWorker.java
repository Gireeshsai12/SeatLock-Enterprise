package com.seatlock.notifications;

import com.seatlock.inventory.InventoryService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name="seatlock.worker",havingValue="true")
public class OutboxWorker {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxWorker.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Delivery delivery;
    private final NotificationPublisher publisher;
    private final InventoryService inventory;

    public OutboxWorker(JdbcTemplate jdbc, TransactionTemplate tx, Delivery delivery,
            ObjectProvider<NotificationPublisher> publisher, InventoryService inventory,
            @Value("${seatlock.notifications}") String transport) {
        if (!java.util.Set.of("database","rabbit","sqs").contains(transport)) {
            throw new IllegalArgumentException("NOTIFICATION_TRANSPORT must be database, rabbit, or sqs.");
        }
        this.jdbc = jdbc;
        this.tx = tx;
        this.delivery = delivery;
        this.publisher = "database".equals(transport) ? null : publisher.getObject();
        this.inventory = inventory;
    }

    @Scheduled(fixedDelay=1000)
    public void dispatch() {
        for (int count=0;count<20;count++) {
            if (!Boolean.TRUE.equals(tx.execute(status -> dispatchOne()))) return;
        }
    }

    private boolean dispatchOne() {
        record Pending(Delivery.Message message, int attempts) {}
        var pending = jdbc.query("""
            select id,recipient,subject,body,attempts from outbox
            where sent_at is null and dead_at is null and next_attempt<=now()
            order by next_attempt,id limit 1 for update skip locked
            """, (rs,n) -> new Pending(new Delivery.Message(rs.getObject(1,UUID.class),
                rs.getString(2),rs.getString(3),rs.getString(4)),rs.getInt(5)));
        if (pending.isEmpty()) return false;
        var item = pending.getFirst();
        try {
            if (publisher == null) delivery.deliver(item.message());
            else publisher.publish(item.message());
        } catch (RuntimeException failure) {
            // Roll back the failed delivery independently before recording retries.
            scheduleRetry(item.message().id(),item.attempts()+1);
            LOG.warn("Notification {} failed on attempt {}",item.message().id(),item.attempts()+1);
            return true;
        }
        jdbc.update("update outbox set sent_at=now() where id=?",item.message().id());
        return true;
    }

    private void scheduleRetry(UUID id, int attempt) {
        jdbc.update("""
            update outbox set attempts=?,next_attempt=now()+make_interval(secs => ?),
            dead_at=case when ?>=5 then now() else null end where id=?
            """,attempt,(double) (1 << attempt),attempt,id);
    }

    @Scheduled(fixedDelay=60000)
    public void reconcileExpiredHolds() {
        inventory.reconcile();
    }
}
