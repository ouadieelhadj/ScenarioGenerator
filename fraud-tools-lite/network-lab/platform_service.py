from __future__ import annotations

import argparse
import asyncio
import json
import os
import uuid

import requests
import uvicorn
from fastapi import FastAPI, HTTPException
from kafka import KafkaProducer


MODEL_URL = os.environ.get("FRAUD_MODEL_URL", "http://127.0.0.1:5001/invocations")
FEAST_URL = os.environ.get("FRAUD_FEAST_URL", "http://127.0.0.1:6566")
KAFKA_BOOTSTRAP = os.environ.get("FRAUD_KAFKA_BOOTSTRAP", "127.0.0.1:9092")
KAFKA_TOPIC = os.environ.get("FRAUD_KAFKA_TOPIC", "fraud.risk-assessment-completed.v1")
FEATURES = [
    "amount_deviation",
    "attempts_last_hour",
    "device_novelty",
    "location_novelty",
    "beneficiary_age_minutes",
    "graph_group_size",
    "behavioral_deviation",
    "threat_intelligence_signal",
]
app = FastAPI(title="FuturPayment Fraud Platform Lab", version="1.0")
producer = None


def enrich_from_feast(payload: dict) -> dict:
    instrument_token = payload.get("instrumentToken")
    if not instrument_token or instrument_token == "UNSPECIFIED":
        return {"source": "REQUEST_ONLY", "features": payload}
    requested = [
        "fraud_transaction_features:attempts_last_hour",
        "fraud_transaction_features:amount_deviation",
        "fraud_transaction_features:device_novelty",
        "fraud_transaction_features:graph_group_size",
    ]
    response = requests.post(
        FEAST_URL + "/get-online-features",
        json={"features": requested, "entities": {"instrument_id": [instrument_token]}},
        timeout=5,
    )
    response.raise_for_status()
    content = response.json()
    names = content["metadata"]["feature_names"]
    values = {name: result["values"][0] for name, result in zip(names, content["results"])}
    enriched = dict(payload)
    for name in ["attempts_last_hour", "amount_deviation", "device_novelty", "graph_group_size"]:
        if name in values:
            enriched[name] = values[name]
    return {"source": "FEAST_HTTP", "features": enriched}


def publish_decision(result: dict) -> dict:
    global producer
    if producer is None:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP,
            acks="all",
            value_serializer=lambda value: json.dumps(value).encode("utf-8"),
        )
    metadata = producer.send(KAFKA_TOPIC, result).get(timeout=10)
    producer.flush(timeout=10)
    return {"topic": metadata.topic, "partition": metadata.partition, "offset": metadata.offset}


def score(payload: dict) -> dict:
    missing = [name for name in ["memberId", "transactionReference", *FEATURES] if name not in payload]
    if missing:
        raise ValueError("Missing fields: " + ", ".join(missing))
    enrichment = enrich_from_feast(payload)
    scoring_payload = enrichment["features"]
    body = {
        "dataframe_split": {
            "columns": FEATURES,
            "data": [[scoring_payload[name] for name in FEATURES]],
        }
    }
    response = requests.post(MODEL_URL, json=body, timeout=10)
    response.raise_for_status()
    prediction = response.json()["predictions"][0]
    recommended = prediction["recommendedAction"]
    enforced = "ALERT" if payload.get("decisionMode") == "ALERT_ONLY" and recommended != "ALLOW" else recommended
    result = {
        "memberId": payload["memberId"],
        "transactionReference": payload["transactionReference"],
        "riskScore": prediction["riskScore"],
        "recommendedAction": recommended,
        "enforcedAction": enforced,
        "decisionMode": payload.get("decisionMode", "DECISION"),
        "modelSource": "MLFLOW_HTTP",
        "featureSource": enrichment["source"],
    }
    result["eventPublication"] = publish_decision(result)
    return result


@app.get("/health")
def health():
    return {"status": "UP", "role": "FRAUD_PLATFORM"}


@app.post("/v1/risk-assessments")
def risk_assessment(payload: dict):
    try:
        return score(payload)
    except (ValueError, requests.RequestException, KeyError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


async def handle_iso(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    connection_id = str(uuid.uuid4())
    try:
        while True:
            line = await reader.readline()
            if not line:
                break
            try:
                payload = json.loads(line.decode("utf-8"))
                result = await asyncio.to_thread(score, payload)
                result["isoConnectionId"] = connection_id
                result["transport"] = "ISO8583_PERSISTENT_LAB"
            except Exception as exc:
                result = {"error": str(exc), "isoConnectionId": connection_id}
            writer.write((json.dumps(result) + "\n").encode("utf-8"))
            await writer.drain()
    finally:
        writer.close()
        await writer.wait_closed()


@app.on_event("startup")
async def start_iso_listener():
    host = os.environ.get("FRAUD_PLATFORM_ISO_HOST", "127.0.0.3")
    port = int(os.environ.get("FRAUD_PLATFORM_ISO_PORT", "8583"))
    app.state.iso_server = await asyncio.start_server(handle_iso, host, port)


@app.on_event("shutdown")
async def stop_iso_listener():
    app.state.iso_server.close()
    await app.state.iso_server.wait_closed()
    if producer is not None:
        producer.close(timeout=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.3")
    parser.add_argument("--port", type=int, default=8089)
    args = parser.parse_args()
    uvicorn.run(app, host=args.host, port=args.port, workers=1)
