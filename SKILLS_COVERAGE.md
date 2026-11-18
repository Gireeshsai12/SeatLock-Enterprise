# Skills coverage

Paths below are relative to the project directory. Backend Java paths share the prefix backend/src/main/java/com/seatlock. Execution evidence and exclusions are in VERIFICATION_REPORT.md.

| Skill or stack item | Evidence |
| --- | --- |
| Java 21, OOP, inheritance, polymorphism | backend/pom.xml, shared/ErrorHandler.java extends Spring MVC's handler, notifications/NotificationPublisher.java has RabbitMQ and SQS implementations |
| Interfaces, generics, collections, streams, exceptions | events/EventRepository.java, shared/PageWindow.java, shared/ApiError.java, reservations/ReservationService.java seat normalization |
| Concurrency and transactions | inventory/InventoryService.java deterministic row locks, reservations/ReservationService.java atomic mutations, notifications/OutboxWorker.java SKIP LOCKED, tests/smoke.py competing carts |
| Python 3.12 target, automation and analysis | tests/smoke.py, scripts/analyze-performance.py, infra/aws/notification.py |
| JavaScript and TypeScript | frontend/vite.config.js, frontend/src/api.ts, frontend/src/main.tsx, frontend/e2e/demo.spec.ts |
| Git and GitHub Actions | .gitignore, .github/workflows/verify.yml |
| Bash, Linux, PowerShell | start.sh, scripts/start-native.sh, scripts/deploy-kind.sh, start.ps1 |
| Spring Boot, Web, REST, Jakarta Validation | SeatLock.java, events/EventController.java, reservations/ReservationController.java, shared/ErrorHandler.java |
| Spring Security, JWT, OAuth2, RBAC | security/SecurityConfiguration.java, security/Tokens.java, identity/IdentityService.java |
| Spring Data JPA, Hibernate, optimistic locking | events/Event.java with @Version, events/EventRepository.java, events/EventService.java |
| PostgreSQL, Flyway, indexes, pessimistic locks | backend/src/main/resources/db/migration, inventory/InventoryService.java, reservations/ReservationRepository.java |
| SQL analysis and tuning | tests/explain.sql, event and reservation indexes in V1__schema.sql |
| Redis and atomic counters | security/AuthRateLimit.java and docker-compose.yml |
| RabbitMQ and SQS | notifications/NotificationPublisher.java, RabbitTransport.java, SqsTransport.java, infra/aws/services.tf |
| Workers, retries, DLQ, outbox | notifications/OutboxWorker.java, Outbox.java, RabbitTransport.java, backend/src/main/resources/application.yml, infra/aws/services.tf |
| Optional SMTP and simulated email | notifications/Delivery.java, infra/aws/notification.py |
| React, accessible forms, dashboards, loading, error boundary | frontend/src/main.tsx, frontend/src/style.css and browser tests |
| OpenAPI, Swagger UI, Actuator | backend/pom.xml, backend/src/main/resources/application.yml, docs/API.md |
| JUnit 5 and Mockito | backend/src/test/java/com/seatlock/DomainTest.java and IdentityServiceTest.java |
| Testcontainers | backend/src/test/java/com/seatlock/PostgresContainerTest.java |
| Playwright | frontend/e2e/demo.spec.ts and frontend/playwright.config.ts |
| Postman and Newman | tests/SeatLock.postman_collection.json and .github/workflows/verify.yml |
| k6 and coverage | tests/k6.js, JaCoCo in backend/pom.xml |
| Docker, Compose, Nginx | backend/Dockerfile, frontend/Dockerfile, docker-compose.yml, frontend/nginx.conf |
| Terraform, AWS IAM, EC2, S3, RDS, VPC, security groups | infra/aws/network.tf, services.tf and versions.tf |
| Lambda, SQS, CloudWatch | infra/aws/lambda.tf, services.tf and notification.py |
| Kubernetes | infra/k8s/app.yaml, dependencies.yaml, scripts/deploy-kind.sh |
