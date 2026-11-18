# API reference

Swagger UI is at /swagger-ui/index.html and the OpenAPI JSON is at /v3/api-docs. Business routes use the prefix /api/v1. Authenticated requests send an Authorization header with the Bearer access token. Access tokens expire after an hour.

| Method | Route | Behavior |
| --- | --- | --- |
| GET | /auth/options | Demo and Google sign-in flags |
| POST | /auth/register | Create customer and return session |
| POST | /auth/login | Sign in and return session |
| GET | /events | Search with q, category, and sort |
| GET | /events/{id} | Event details |
| GET | /events/{id}/seats | Live inventory snapshot |
| POST | /reservations | Hold one to eight distinct seats |
| POST | /reservations/{id}/checkout | Record simulated payment |
| POST | /reservations/{id}/cancel | Release hold or record simulated refund |
| GET | /reservations | Load account history |
| GET or POST | /admin/venues | List or create layouts |
| PUT | /admin/venues/{id} | Update venue or unused layout |
| POST | /admin/events | Create event and initialize seat map |
| PUT | /admin/events/{id} | Edit using current version |
| GET | /admin/analytics | Inventory, commerce, and outbox counts |
| GET | /admin/audit | Audit history |
| GET | /admin/notifications | Delivery summaries |
| POST | /admin/inventory/reconcile | Release expired seat assignments |
| GET | /actuator/health | Public health at the root |
| GET | /actuator/metrics | Administrator-only metrics at the root |

Lists accept zero-based page and sizes between 1 and 100. Events support upcoming, price, or title sorting. Registration requires email and a password of at least 10 characters and at most 72 UTF-8 bytes.

```json
{"eventId": 1, "seats": [{"row": 1, "number": 1}, {"row": 1, "number": 2}]}
```

Checkout uses APPROVE or DECLINE in the scenario field. A decline returns HTTP 200 and PAYMENT_FAILED because it is a completed demo outcome. Invalid data returns 400, unavailable or expired assignments return 409, and an inaccessible reservation returns 404. Checkout and cancellation safely handle repeated requests against an existing reservation. If the network fails during hold creation, check account history before retrying.
