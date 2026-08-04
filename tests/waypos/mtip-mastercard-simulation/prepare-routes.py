#!/usr/bin/env python3
"""Route only the MTIP certification BINs to Mastercard DMAS."""

from __future__ import annotations

import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parent
BASE_URL = os.environ.get("WAY_POS_SERVER_BASE_URL", "http://127.0.0.1:8530").rstrip("/")
ROUTES_URL = f"{BASE_URL}/api/admin/waypos/v1/bin-routes"
INTERFACE_CODE = os.environ.get("MTIP_ROUTE_INTERFACE", "DMAS_MEMBER")


def call(method: str, payload: dict[str, object] | None = None) -> object:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = Request(
        ROUTES_URL,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urlopen(request, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code} pendant la preparation des routes: {detail}") from exc
    except URLError as exc:
        raise SystemExit(f"ServerPOS inaccessible sur {BASE_URL}: {exc.reason}") from exc


def deactivate(route_id: object) -> None:
    request = Request(f"{ROUTES_URL}/{route_id}", method="DELETE")
    try:
        with urlopen(request, timeout=10):
            return
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code} pendant la desactivation d'une route: {detail}") from exc
    except URLError as exc:
        raise SystemExit(f"ServerPOS inaccessible sur {BASE_URL}: {exc.reason}") from exc


def required_bins() -> set[str]:
    bins: set[str] = set()
    for path in sorted((ROOT / "requests").glob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8-sig"))
        for step in document.get("steps", []):
            pan = str(step.get("request", {}).get("fields", {}).get("2", ""))
            if pan:
                if not pan.isdigit() or len(pan) < 6:
                    raise SystemExit(f"DE2 invalide dans {path.name}")
                bins.add(pan[:6])
    if not bins:
        raise SystemExit("Aucun BIN trouve dans requests/")
    return bins


def main() -> int:
    bins = required_bins()
    existing = call("GET")
    deactivated = 0
    for route in existing:
        exact_mtip_bin = (
            str(route.get("binFrom")) in bins
            and str(route.get("binTo")) == str(route.get("binFrom"))
        )
        if exact_mtip_bin and route.get("active") and route.get("interfaceCode") != INTERFACE_CODE:
            deactivate(route["id"])
            route["active"] = False
            deactivated += 1
    active_dmas = {
        (str(route["binFrom"]), str(route["binTo"]))
        for route in existing
        if route.get("active") and route.get("interfaceCode") == INTERFACE_CODE
    }
    created = 0
    for bin_value in sorted(bins):
        if (bin_value, bin_value) in active_dmas:
            continue
        call(
            "POST",
            {
                "binFrom": bin_value,
                "binTo": bin_value,
                "interfaceCode": INTERFACE_CODE,
                "priority": 100,
            },
        )
        created += 1
    print(
        f"Routes BIN MTIP vers {INTERFACE_CODE} pretes: "
        f"{created} creee(s), {deactivated} ancienne(s) desactivee(s), "
        f"{len(bins)} requise(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
