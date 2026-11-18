# Troubleshooting

| Symptom | Check |
| --- | --- |
| Docker command unavailable | Start Docker Desktop or install Docker Engine with the Compose v2 plugin. Run the included startup script from the extracted folder. |
| Compose says secrets are missing | Run the startup script to generate .env. Keep .env with the associated volumes. |
| Port 8080 is in use | Add WEB_PORT=8088 to .env for Compose or set WEB_PORT=8088 before the native command. Use that port for tests and URLs. |
| Native PostgreSQL binaries are missing | Set PG_BIN to the installed PostgreSQL bin directory. Run native startup as a non-root user. |
| Native startup exits | Check .local/backend-build.log, frontend-build.log, postgres.log, api.log, worker.log, and frontend.log. |
| Administrator password fails | Read ADMIN_PASSWORD from the environment file created during the initial startup. Existing database accounts retain the original hash. |
| A session stops working | Sign out and sign back in. Access tokens expire after an hour. |
| Seat becomes unavailable during checkout | A conflicting request won, the hold expired, or the event started. Refresh the seat map and create a new hold. |
| Notifications stay pending | Use docker compose logs worker rabbit for Compose or .local/worker.log for native startup. Inspect outbox counters in Operations. |
| Google button is absent | Google client configuration is optional. See deployment notes. |

Stop Compose with docker compose down. It keeps named volumes. To erase all demo data intentionally, run docker compose down -v. A new native state can be created with SEATLOCK_STATE pointing to a fresh directory.
