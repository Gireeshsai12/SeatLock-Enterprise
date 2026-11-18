"""SQS consumer for the optional AWS demo. Emits a privacy-safe simulated delivery log."""
import json
import logging
import uuid

LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)


def handle(event, _context):
    failures = []
    for record in event["Records"]:
        try:
            notification = json.loads(record["body"])
            notification_id = uuid.UUID(notification["id"])
            if not all(isinstance(notification[key], str) and notification[key].strip()
                       for key in ("recipient", "subject", "body")):
                raise ValueError("Malformed notification")
            LOGGER.info("SIMULATED notification delivery id=%s", notification_id)
        except (KeyError, ValueError, TypeError):
            failures.append({"itemIdentifier": record["messageId"]})
    return {"batchItemFailures": failures}
