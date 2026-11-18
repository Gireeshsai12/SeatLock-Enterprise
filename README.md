# SeatLock Enterprise

SeatLock is a reservation and commerce demo for concerts, movies, sports, conferences, and performances. It uses Java 21, Spring Boot, PostgreSQL, and React with TypeScript.

## Start with Docker Compose

Install Docker Desktop on Windows or macOS, or Docker Engine with Compose v2 on Linux. First startup downloads public dependencies and images. The demo can then operate locally without paid services or credentials supplied by you.

**Windows PowerShell**, from the extracted project directory

```powershell
powershell -ExecutionPolicy Bypass -File .\start.ps1
```

**Linux or macOS**

```bash
bash ./start.sh
```

The script creates local credentials in .env, builds the app, and starts PostgreSQL, Redis, RabbitMQ, the API, the worker, and Nginx. Database migrations and the sample catalog initialize automatically. Sign in as admin@seatlock.local with the generated ADMIN_PASSWORD from .env. Create a customer using any address you control or a made-up example.test address.

## Start without Docker on Linux or macOS

Install **JDK 21**, **Maven 3.9+**, **Node.js 22.12+ and npm**, **PostgreSQL 16 binaries**, Bash, and curl. Run from an ordinary user account. The script creates and stops its own loopback PostgreSQL cluster, builds from the manifests, and starts Spring Boot, a database-only notification worker, and the Vite preview server.

```bash
bash ./scripts/start-native.sh
```

If PostgreSQL is installed but the script cannot find initdb, use PG_BIN to point to the PostgreSQL bin directory. The generated administrator password is in .local/demo.env. Stop the foreground stack with Ctrl+C. Published Docker startup is available for Windows. See the verification report for the OS and startup paths actually exercised.

| Interface | URL |
| --- | --- |
| Web app | http://localhost:8080/ |
| API health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

## Demonstrate the workflows

1. Register and select a concert. Hold seats, then approve or decline the demo checkout.
2. Open My reservations. Cancel a confirmed booking and observe the simulated refund. Released seats return to the event map.
3. Sign in as the administrator. Create a venue and an event in Operations. Inspect audit entries and notification deliveries, and run expired-hold reconciliation.
4. Sign in as a second customer in a separate browser session and attempt to hold the same seat as the first customer.

Payments and refunds always use the simulator. Default email delivery persists a simulated delivery entry. No card data is collected. Set HOLD_SECONDS=5 in the native process environment for the expiration test. The default hold is five minutes.

## Test commands

```bash
mvn -B -f backend/pom.xml verify
# In a second shell, load generated demo credentials from .env or .local/demo.env.
set -a
source .local/demo.env
set +a
python3 tests/smoke.py
cd frontend
npm ci
npx playwright install chromium
npm run test:e2e
```

For the Compose stack, source .env in the test shell. JaCoCo reports are generated under backend/target/site/jacoco. Additional runnable checks include the following.

```bash
npx --yes newman@6.2.1 run tests/SeatLock.postman_collection.json
k6 run tests/k6.js
python3 scripts/analyze-performance.py
docker compose exec -T db psql -U seatlock -d seatlock < tests/explain.sql
```

See [architecture](docs/ARCHITECTURE.md), [API reference](docs/API.md), [deployment notes](docs/DEPLOYMENT.md), [troubleshooting](TROUBLESHOOTING.md), [skills coverage](SKILLS_COVERAGE.md), and [verification](VERIFICATION_REPORT.md).
