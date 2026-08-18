from __future__ import annotations

import json
import sys
import time
import uuid
from pathlib import Path

from kafka import KafkaConsumer


root = Path(r"D:\MoneyCore\ScenarioGenerator\fraud-tools-lite")
expected = set(sys.argv[1:])
consumer = KafkaConsumer(
    "fraud.risk-assessment-completed.v1",
    bootstrap_servers="127.0.0.1:9092",
    auto_offset_reset="earliest",
    enable_auto_commit=False,
    group_id="fraud-lite-proof-" + str(uuid.uuid4()),
    consumer_timeout_ms=12000,
)
found = []
deadline = time.time() + 15
try:
    for message in consumer:
        value = json.loads(message.value.decode("utf-8"))
        reference = value.get("transactionReference")
        if reference in expected:
            found.append(
                {
                    "topic": message.topic,
                    "partition": message.partition,
                    "offset": message.offset,
                    "value": value,
                }
            )
        if {item["value"]["transactionReference"] for item in found} == expected or time.time() > deadline:
            break
finally:
    consumer.close()

if {item["value"]["transactionReference"] for item in found} != expected:
    raise SystemExit("KAFKA_EVIDENCE_MISSING")
(root / "evidence" / "kafka-platform-events.json").write_text(json.dumps(found, indent=2), encoding="utf-8")
print("KAFKA_PLATFORM_EVENTS_OK=" + str(len(found)))
