"""Executable API integration suite for a fresh SeatLock demo."""
import concurrent.futures
import datetime as dt
import json
import os
import secrets
import time
import unittest
import urllib.error
import urllib.request

BASE = os.getenv("BASE_URL", "http://127.0.0.1:8080").rstrip("/")
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, *, data=None, token=None, method=None):
    headers = {"Accept": "text/html" if path == "/" else "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(
        BASE + path, json.dumps(data).encode() if data is not None else None,
        headers, method=method)
    try:
        response = OPENER.open(request, timeout=15)
    except urllib.error.HTTPError as failure:
        response = failure
    with response:
        payload = response.read()
        if payload and "json" in response.headers.get("Content-Type", ""):
            payload = json.loads(payload)
        return response.status, payload


def api(path, **kwargs):
    return call("/api/v1" + path, **kwargs)


class Demo(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        password = os.environ["ADMIN_PASSWORD"]
        status, result = api("/auth/login", data={"email": "admin@seatlock.local", "password": password})
        assert status == 200, f"Admin login returned {status}"
        cls.admin = result["accessToken"]
        cls.password = secrets.token_urlsafe(18)

    def register(self):
        email = f"buyer-{secrets.token_hex(8)}@example.test"
        status, result = api("/auth/register", data={"email": email, "password": self.password})
        self.assertEqual(201, status)
        return email, result["accessToken"]

    def new_event(self):
        status, venue = api("/admin/venues", token=self.admin, data={
            "name": "Verification Hall", "city": "Lowell", "rows": 2, "seatsPerRow": 8})
        self.assertEqual(201, status)
        details = {"title": "Integration " + secrets.token_hex(4), "category": "CONCERT", "venueId": venue["id"],
                   "startsAt": (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=35)).isoformat(), "priceCents": 3200}
        status, event = api("/admin/events", token=self.admin, data=details)
        self.assertEqual(201, status, event)
        return event, details

    def hold(self, event, token, *numbers):
        return api("/reservations", token=token, data={"eventId": event["id"],
            "seats": [{"row": 1, "number": number} for number in numbers]})

    def test_01_health_docs_and_validation(self):
        status, health = call("/actuator/health")
        self.assertEqual((200, "UP"), (status, health["status"]))
        status, homepage = call("/")
        self.assertEqual(200, status)
        self.assertIn(b"<title>SeatLock Enterprise</title>", homepage)
        self.assertIn("/api/v1/reservations", call("/v3/api-docs")[1]["paths"])
        status, listings = api("/events?q=Midnight&category=CONCERT&sort=price&size=1")
        self.assertEqual(200, status)
        self.assertEqual(["Midnight Jazz Sessions"], [event["title"] for event in listings])
        self.assertEqual(400, api("/events?size=0")[0])
        self.assertEqual(400, api("/events?sort=invalid")[0])
        _, token = self.register()
        self.assertEqual(400, api("/reservations", token=token, data={"eventId": 1, "seats": [None]})[0])

    def test_02_credentials_and_authorization(self):
        email, token = self.register()
        self.assertEqual(200, api("/auth/login", data={"email": email.upper(), "password": self.password})[0])
        self.assertEqual(409, api("/auth/register", data={"email": email, "password": self.password})[0])
        self.assertEqual(401, api("/auth/login", data={"email": email, "password": "incorrect password"})[0])
        self.assertEqual(401, api("/reservations")[0])
        self.assertEqual(403, api("/admin/analytics", token=token)[0])

    def test_03_admin_conflicts_and_catalog(self):
        event, details = self.new_event()
        details.update(title=details["title"] + " edited", version=event["version"])
        self.assertEqual(200, api(f"/admin/events/{event['id']}", token=self.admin, method="PUT", data=details)[0])
        self.assertEqual(409, api(f"/admin/events/{event['id']}", token=self.admin, method="PUT", data=details)[0])
        self.assertEqual(409, api(f"/admin/venues/{details['venueId']}", token=self.admin, method="PUT",
            data={"name": "In-use venue", "city": "Lowell", "rows": 3, "seatsPerRow": 8})[0])
        self.assertEqual(16, len(api(f"/events/{event['id']}/seats")[1]))
        self.assertEqual(200, api("/admin/audit", token=self.admin)[0])
        self.assertEqual(200, api("/admin/inventory/reconcile", token=self.admin, method="POST")[0])

    def test_04_paid_checkout_and_refund_are_idempotent(self):
        event, _ = self.new_event()
        _, token = self.register()
        _, other = self.register()
        self.assertEqual(400, self.hold(event,token,1,1)[0])
        status, held = self.hold(event,token,2,3)
        self.assertEqual(201, status)
        self.assertEqual(6400, held["totalCents"])
        route = f"/reservations/{held['id']}"
        self.assertEqual(404, api(route+"/checkout",token=other,data={"scenario":"APPROVE"})[0])
        self.assertEqual(409, self.hold(event,other,3)[0])
        for _ in range(2):
            status, confirmed = api(route+"/checkout",token=token,data={"scenario":"APPROVE"})
            self.assertEqual((200,"CONFIRMED"), (status,confirmed["status"]))
        for _ in range(2):
            status, cancelled = api(route+"/cancel",token=token,method="POST")
            self.assertEqual((200,"CANCELLED"),(status,cancelled["status"]))
        self.assertEqual("REFUNDED",api("/reservations",token=token)[1][0]["payment_status"])
        self.assertEqual(201,self.hold(event,other,3)[0])

        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            _, delivered = api("/admin/notifications?size=100",token=self.admin)
            subjects = {item["subject"] for item in delivered if item["delivery_mode"] == "SIMULATED"}
            if {"Reservation confirmed","Reservation cancelled and demo refund recorded"} <= subjects:
                break
            time.sleep(0.5)
        else:
            self.fail("Expected worker to persist simulated confirmation and cancellation deliveries.")

    def test_05_decline_releases_seats(self):
        event, _ = self.new_event()
        _, token = self.register()
        _, other = self.register()
        _, held = self.hold(event,token,2)
        status, declined = api(f"/reservations/{held['id']}/checkout",token=token,data={"scenario":"DECLINE"})
        self.assertEqual((200,"PAYMENT_FAILED"),(status,declined["status"]))
        self.assertEqual("DECLINED",api("/reservations",token=token)[1][0]["payment_status"])
        self.assertEqual(201,self.hold(event,other,2)[0])

    def test_06_concurrent_multiseat_requests_have_one_winner(self):
        event, _ = self.new_event()
        _, first = self.register()
        _, second = self.register()
        import threading
        start = threading.Barrier(2, timeout=5)

        def compete(token, seats):
            start.wait()
            return self.hold(event,token,*seats)

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            outcomes = [pool.submit(compete,first,(2,3)),pool.submit(compete,second,(3,2))]
            statuses = sorted(future.result()[0] for future in outcomes)
        self.assertEqual([201,409],statuses)

    def test_07_expired_cart_cannot_checkout_and_is_reusable(self):
        event, _ = self.new_event()
        _, token = self.register()
        _, other = self.register()
        status, held = self.hold(event,token,4)
        self.assertEqual(201,status)
        expiry = dt.datetime.fromisoformat(held["expiresAt"].replace("Z","+00:00"))
        wait = (expiry - dt.datetime.now(dt.timezone.utc)).total_seconds()
        if wait > 12:
            self.skipTest("Restart with HOLD_SECONDS=5 to execute expiration test.")
        time.sleep(max(0,wait)+0.25)
        status, error = api(f"/reservations/{held['id']}/checkout",token=token,data={"scenario":"APPROVE"})
        self.assertEqual((409,"HOLD_EXPIRED"),(status,error["code"]))
        self.assertEqual("EXPIRED",api("/reservations",token=token)[1][0]["status"])
        self.assertEqual(201,self.hold(event,other,4)[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
