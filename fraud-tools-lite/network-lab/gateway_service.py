from __future__ import annotations

import argparse
import json
import os
import socket
import threading

import requests
import uvicorn
from fastapi import FastAPI, HTTPException


PLATFORM_URL = os.environ.get("FRAUD_PLATFORM_URL", "http://127.0.0.3:8089")
ISO_HOST = os.environ.get("FRAUD_PLATFORM_ISO_HOST", "127.0.0.3")
ISO_PORT = int(os.environ.get("FRAUD_PLATFORM_ISO_PORT", "8583"))
app = FastAPI(title="FuturPayment Fraud Bank Gateway Lab", version="1.0")


class PersistentIsoLink:
    def __init__(self):
        self.socket = None
        self.reader = None
        self.lock = threading.Lock()

    def request(self, payload: dict) -> dict:
        with self.lock:
            if self.socket is None:
                self.socket = socket.create_connection((ISO_HOST, ISO_PORT), timeout=5)
                self.reader = self.socket.makefile("rb")
            self.socket.sendall((json.dumps(payload) + "\n").encode("utf-8"))
            line = self.reader.readline()
            if not line:
                raise ConnectionError("Persistent ISO link closed")
            return json.loads(line.decode("utf-8"))

    def close(self):
        if self.reader:
            self.reader.close()
        if self.socket:
            self.socket.close()


iso_link = PersistentIsoLink()


def normalize(payload: dict) -> dict:
    required = ["bankId", "transactionReference"]
    missing = [name for name in required if not payload.get(name)]
    if missing:
        raise ValueError("Missing bank fields: " + ", ".join(missing))
    return {
        "memberId": payload["bankId"],
        "transactionReference": payload["transactionReference"],
        "decisionMode": payload.get("decisionMode", "DECISION"),
        "amount_deviation": payload.get("amountDeviation", 0.0),
        "attempts_last_hour": payload.get("attemptsLastHour", 0),
        "device_novelty": payload.get("deviceNovelty", 0.0),
        "location_novelty": payload.get("locationNovelty", 0.0),
        "beneficiary_age_minutes": payload.get("beneficiaryAgeMinutes", 99999),
        "graph_group_size": payload.get("graphGroupSize", 0),
        "behavioral_deviation": payload.get("behavioralDeviation", 0.0),
        "threat_intelligence_signal": payload.get("threatIntelligenceSignal", 0),
        "instrumentToken": payload.get("instrumentToken", "UNSPECIFIED"),
    }


@app.get("/health")
def health():
    return {"status": "UP", "role": "BANK_GATEWAY", "platformTarget": PLATFORM_URL}


@app.post("/v1/bank/authorizations")
def authorize_rest(payload: dict):
    try:
        normalized = normalize(payload)
        response = requests.post(PLATFORM_URL + "/v1/risk-assessments", json=normalized, timeout=10)
        response.raise_for_status()
        result = response.json()
        result["gatewayTransport"] = "REST"
        return result
    except (ValueError, requests.RequestException) as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/v1/bank/iso-authorizations")
def authorize_iso(payload: dict):
    try:
        result = iso_link.request(normalize(payload))
        result["gatewayTransport"] = "ISO8583_PERSISTENT_LAB"
        return result
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.on_event("shutdown")
def shutdown():
    iso_link.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.2")
    parser.add_argument("--port", type=int, default=8090)
    args = parser.parse_args()
    uvicorn.run(app, host=args.host, port=args.port, workers=1)
