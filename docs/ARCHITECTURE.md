# Architecture

The Spring Boot API is a modular monolith. PostgreSQL stores authoritative inventory and reservations. A separate worker runs from the same build. Compose uses Redis for authentication throttling and RabbitMQ for notifications. Native startup uses the PostgreSQL outbox as its transport.

```mermaid
flowchart TD
    Browser["React browser"] --> Proxy["Nginx or Vite"]
    Proxy --> API["Spring Boot API"]
    API --> PG["PostgreSQL"]
    API --> Redis["Redis auth throttling"]
    PG --> Worker["Outbox worker"]
    Worker --> Rabbit["RabbitMQ"]
    Worker --> Delivery["Notification delivery"]
    Rabbit --> Delivery
    Delivery --> PG
    Worker --> SQS["Optional AWS SQS"]
    SQS --> Lambda["Lambda simulated delivery"]
```

```mermaid
sequenceDiagram
    participant C as Customer
    participant A as API
    participant P as PostgreSQL
    participant W as Worker
    C->>A: Hold selected seats
    A->>P: Lock seats in row and seat order
    P-->>A: Assign temporary hold
    A-->>C: Return cart and price snapshot
    C->>A: Check out
    A->>P: Lock reservation and seats
    A->>P: Commit payment, assignment, audit and outbox
    A-->>C: Return payment outcome
    W->>P: Claim outbox row with SKIP LOCKED
    W->>W: Deliver or publish with acknowledgment
```

A booking is a seat assignment with no expiration. Checkout and cancellation serialize on the reservation row, verify ownership, and then lock assigned seats. Declines and cancellations release seats. Historical order items remain independent of inventory ownership. Reads treat expired holds as available before cleanup. The browser refreshes the map every two seconds.

JPA optimistic versions protect event edits. A venue row serializes layout changes and event creation. Layout geometry is a numbered rectangle and locks once an event references it. Prices are snapshotted at hold time.

Rabbit publisher confirms and mandatory routing prevent marking the outbox sent before broker acknowledgment. A crash can still cause redelivery. Local delivery deduplicates by message UUID. SMTP can send a duplicate if the worker dies after the SMTP server accepts the message and before the delivery transaction commits. Lambda emits simulated delivery IDs to CloudWatch, where duplicate log entries are possible.

The worker retries publication with exponential delays and marks exhaustion as dead. RabbitMQ consumers retry and dead-letter rejected messages. SQS uses its queue redrive policy. The admin endpoint reports database outbox counts. Broker dead letters are observed separately.
