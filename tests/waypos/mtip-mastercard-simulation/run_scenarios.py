#!/usr/bin/env python3
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent
DEFAULT_URL = os.environ.get("WAY_POS_SIMULATOR_BASE_URL", "http://127.0.0.1:8532")


def fetch_json(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(body)
        except json.JSONDecodeError:
            parsed = {"error": body[:500]}
        return exc.code, parsed


def runtime_values(sequence):
    now = dt.datetime.now(dt.timezone.utc)
    stan = f"{(int(now.timestamp()) + sequence) % 999999 + 1:06d}"
    return {
        "${MMDDHHMMSS}": now.strftime("%m%d%H%M%S"),
        "${HHMMSS}": now.strftime("%H%M%S"),
        "${MMDD}": now.strftime("%m%d"),
        "${STAN}": stan,
        "${RRN}": now.strftime("%m%d") + stan + "00",
        "${WAY_POS_TERMINAL_ID}": os.environ.get("WAY_POS_TERMINAL_ID", "TERM0001"),
        "${WAY_POS_MERCHANT_ID}": os.environ.get("WAY_POS_MERCHANT_ID", "MERCHANT0000001"),
    }


def substitute(value, replacements):
    if isinstance(value, dict):
        return {key: substitute(item, replacements) for key, item in value.items()}
    if isinstance(value, list):
        return [substitute(item, replacements) for item in value]
    if isinstance(value, str):
        for token, replacement in replacements.items():
            value = value.replace(token, replacement)
    return value


def masked_pan(request):
    pan = request.get("fields", {}).get("2", "")
    return "ABSENT" if len(pan) < 10 else pan[:6] + "*" * (len(pan) - 10) + pan[-4:]


def run_case(path, base_url, sequence_base=0):
    scenario = json.loads(path.read_text(encoding="utf-8-sig"))
    result = {"testCase": scenario["testCase"], "classification": scenario["classification"], "status": "SKIPPED_TPE_ONLY", "steps": []}
    steps = scenario.get("steps", [])
    if not steps:
        return result
    result["status"] = "PASS"
    for index, step in enumerate(steps, start=1):
        payload = substitute(step["request"], runtime_values(sequence_base + index))
        payload["macEnabled"] = os.environ.get("MTIP_MAC_ENABLED", "true").lower() == "true"
        status, response = fetch_json(base_url.rstrip("/") + "/api/simulator/v1/transactions/field-map", payload)
        expected = step["expected"]
        failures = []
        if status != 200:
            failures.append(f"HTTP {status}: {response.get('error', 'unknown error')}")
        else:
            for key in ("responseMti", "responseCode", "approved"):
                if response.get(key) != expected.get(key):
                    failures.append(f"{key}: expected={expected.get(key)} actual={response.get(key)}")
            if expected.get("emvResponseRequired") and not response.get("emvResponseHex"):
                failures.append("emvResponseHex is required")
            if payload["macEnabled"] and not response.get("macVerified", False):
                failures.append("response MAC is not verified")
        result["steps"].append({
            "id": step["id"], "panMasked": masked_pan(payload), "httpStatus": status,
            "responseMti": response.get("responseMti"), "responseCode": response.get("responseCode"),
            "approved": response.get("approved"), "macVerified": response.get("macVerified"),
            "emvResponsePresent": bool(response.get("emvResponseHex")),
            "status": "PASS" if not failures else "FAIL", "failures": failures})
        if failures:
            result["status"] = "FAIL"
            break
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("test_case", nargs="?")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--base-url", default=DEFAULT_URL)
    args = parser.parse_args()
    status, health = fetch_json(args.base_url.rstrip("/") + "/api/simulator/v1/health")
    if status != 200 or health.get("status") != "UP":
        print("ERREUR: wayPosSimulator indisponible", file=sys.stderr)
        return 2
    request_dir = ROOT / "requests"
    if args.all:
        paths = sorted(request_dir.glob("*.json"))
    elif args.test_case:
        paths = [request_dir / f"{args.test_case.lower().replace('.', '-')}.json"]
    else:
        parser.error("indiquer un test case ou --all")
    missing = [str(path) for path in paths if not path.exists()]
    if missing:
        print("ERREUR: scénario absent: " + ", ".join(missing), file=sys.stderr)
        return 2
    results = [run_case(path, args.base_url, index * 100) for index, path in enumerate(paths)]
    report_dir = ROOT / "reports"
    report_dir.mkdir(exist_ok=True)
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    report = {
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "simulatorBaseUrl": args.base_url,
        "summary": {"total": len(results), "passed": sum(x["status"] == "PASS" for x in results),
                    "failed": sum(x["status"] == "FAIL" for x in results),
                    "skippedTpeOnly": sum(x["status"] == "SKIPPED_TPE_ONLY" for x in results)},
        "results": results}
    report_path = report_dir / f"report-{stamp}.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    for item in results:
        print(f"{item['status']:16} {item['testCase']}")
        if item["status"] == "FAIL" and item["steps"]:
            for failure in item["steps"][-1]["failures"]:
                print(f"  - {failure}")
    print(json.dumps(report["summary"], ensure_ascii=False))
    print(f"Rapport: {report_path}")
    return 1 if report["summary"]["failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
