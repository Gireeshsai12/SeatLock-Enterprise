# Verification report

SeatLock Enterprise was built and exercised on Linux using JDK 21.0.9, Maven 3.9.11, Node.js 24.22.3, Python 3.12.14, and PostgreSQL 16.4. Browser tests used Playwright Chromium Headless Shell 140.

## Final results

| Suite or check | Command | Outcome |
| --- | --- | --- |
| Clean first launch | bash ./scripts/start-native.sh | Fresh PostgreSQL data directory, generated credentials, Maven package, npm ci, TypeScript/Vite production build, API, worker, and UI proxy started |
| Stop and restart | Stop the native script and run it again | Graceful service shutdown and successful restart on the existing database. A second fresh PostgreSQL state also started successfully |
| Default URLs | curl to /actuator/health/readiness, /swagger-ui/index.html, and /api/v1/events through the web port | HTTP 200, readiness UP, Swagger UI available, five seeded events |
| Java unit tests | mvn -B -f backend/pom.xml verify | 6 passed, 0 failed. Docker-only Testcontainers migration test skipped |
| PostgreSQL-backed API tests | HOLD_SECONDS=6 on the running API, then python3 tests/smoke.py | 7 passed, 0 failed. Includes negative requests and a two-client contention test |
| Browser end-to-end | cd frontend, then npm run test:e2e | 3 passed, 0 failed. Creation, checkout, refund, administrator view, reconciliation, and mobile filter |
| Postman regression | npx --yes newman@6.2.1 run tests/SeatLock.postman_collection.json | 6 requests and 6 assertions passed |
| Redis auth throttling | Start Redis locally, set AUTH_RATE_LIMIT=true, run login and set the counter over threshold | Redis 7.0.15 accepted auth counter updates and the API returned HTTP 429 RATE_LIMITED |
| RabbitMQ delivery | Run the worker with NOTIFICATION_TRANSPORT=rabbit against a standalone RabbitMQ 4.0.5 and run the paid checkout/refund integration case | 1 passed, 0 failed. Publisher confirms, broker consumption, and both simulated delivery records exercised |
| k6 | k6 run --quiet tests/k6.js | 10,304 requests, 0 failed, all three thresholds passed, HTTP p95 18.73 ms |
| Python performance sample | python3 scripts/analyze-performance.py | 60 requests with six workers, median 17.34 ms, p95 32.62 ms |
| SQL analysis and migrations | Execute tests/explain.sql via PostgreSQL JDBC and query flyway_schema_history | Both EXPLAIN ANALYZE statements executed and both Flyway migrations recorded success |
| Compose syntax and interpolation | Docker Compose 2.40.3 config --quiet with generated-format temporary values | Configuration accepted |
| Bash and PowerShell | bash -n and PowerShell Parser.ParseFile | Bash syntax passed for the three scripts. PowerShell syntax passed on Linux |
| Kubernetes YAML | Parse infra/k8s/app.yaml and infra/k8s/dependencies.yaml | Six objects in each manifest parsed |
| Terraform | terraform fmt -check -recursive and terraform init -backend=false -input=false | Formatting and initialization passed. Provider schema validation could not complete because provider processes failed their sandbox handshake |
| Lambda consumer | Invoke the Python handler with one valid and one malformed SQS message | Local valid message accepted and malformed item returned for retry |

The [JaCoCo CSV](docs/evidence/jacoco.csv) covers the Java unit test run. API, browser, and load tests ran as separate processes and their execution is outside that coverage file. The k6 measurement is for a small local read workload and gives no production capacity estimate.

## Workflows exercised

- Customer registration, case-normalized login, invalid credentials, customer/admin role boundaries, and reservation ownership.
- Event search, filtering, and sorting. Administrator venue and event creation, optimistic edit conflicts, and protection against changing an active venue layout.
- Checkout, payment declines, cancellation, simulated refunds, notification delivery records, repeat checkout/cancellation, expired holds, and reusing released seats.
- Two simultaneous requests attempted to hold the same two seats in opposite order. PostgreSQL admitted one request and returned a seat conflict for the other.
- React loaded data through the same-origin proxy. Chromium completed customer checkout and cancellation, tested a narrow viewport, and used admin reconciliation.

The initial smoke test used an Accept header intended for JSON when fetching the HTML home page. It was corrected and the complete suite reran successfully. Mockito's default inline self-attachment was unavailable here. The repository uses the Mockito subclass mock maker and the complete available Java suite reran successfully. Chromium Headless Shell was used after the system Chromium executable was blocked by the sandbox's Unix-socket restriction.

## Environment boundaries

- Docker Engine was unavailable. Compose configuration was checked. Container startup, image builds, Nginx runtime, and the Docker-only Testcontainers test remain unverified. RabbitMQ was exercised separately. Retry exhaustion and broker dead-letter delivery were outside that run.
- PowerShell received a syntax check. Actual Windows and macOS startup were outside this Linux run.
- Live Google OAuth, SMTP delivery, Kubernetes deployment, AWS Terraform plan/apply, and live SQS/Lambda delivery remain unverified. AWS infrastructure initialization and a local Lambda handler check are reported above. EC2 application installation is a separate operator step described in the deployment guide.
- Seat availability refreshes every two seconds. Venue seat maps support rectangular layouts. Payments, refunds, and default email delivery are simulated. Credential-free demo runtime follows the initial public dependency downloads.
