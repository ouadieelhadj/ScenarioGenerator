from __future__ import annotations

import argparse, json
from pathlib import Path
import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.metrics import confusion_matrix

ROOT = Path(r"D:\MoneyCore\ScenarioGenerator\fraud-tools-lite")
EVIDENCE = ROOT / "evidence"
BANKS = [("MEMBER-OUADIE", "Ouadie Bank"), ("MEMBER-TRESOR", "Tresor Bank"), ("MEMBER-SEDIK", "Sedik Bank")]
SECTORS = ("MONETIQUE", "MOBILE_BANKING")
FEATURES = ("amount_deviation", "attempts", "new_device", "new_location", "young_beneficiary", "graph_group", "behavior", "threat")


def dataset(rows: int, seed: int, suspicious_count: int = 50, fraud_count: int = 3):
    rng = np.random.default_rng(seed)
    x = np.column_stack((rng.gamma(1.5, .7, rows), rng.poisson(1.4, rows), rng.beta(1.1, 7, rows),
        rng.beta(1.0, 8, rows), rng.beta(1.0, 9, rows), rng.poisson(1.2, rows), rng.beta(1.2, 7, rows),
        rng.binomial(1, .001, rows))).astype(np.float32)
    label = np.zeros(rows, dtype=np.int8)  # 0 normal, 1 suspicious, 2 confirmed fraud
    suspicious = rng.choice(np.arange(100, rows), size=suspicious_count, replace=False)
    available = np.setdiff1d(np.arange(100, rows), suspicious, assume_unique=False)
    blind_start = int(rows * .85)
    fraud = np.concatenate((rng.choice(available[available < blind_start], size=max(0, fraud_count - 1), replace=False),
        rng.choice(available[available >= blind_start], size=1, replace=False)))
    label[suspicious] = 1; label[fraud] = 2
    for n, idx in enumerate(suspicious):
        pattern = n % 4
        if pattern == 0: x[idx, 0], x[idx, 1] = 5.0, 8
        elif pattern == 1: x[idx, 2], x[idx, 3] = .92, .88
        elif pattern == 2: x[idx, 4], x[idx, 0] = .95, 3.8
        else: x[idx, 5], x[idx, 6] = 12, .91
    for idx in fraud:
        # Confirmed fraud combines patterns already observable in older suspicious cases.
        x[idx, 2], x[idx, 3], x[idx, 4], x[idx, 5], x[idx, 6] = .99, .97, .98, 18, .99
    return x, label


def baseline(x):
    return ((x[:, 0] >= 4.5) & (x[:, 1] >= 6)) | (x[:, 7] >= 1)


def metrics(labels, predicted):
    positive = labels > 0; tn, fp, fn, tp = confusion_matrix(positive, predicted, labels=[False, True]).ravel()
    return {"truePositive": int(tp), "falsePositive": int(fp), "trueNegative": int(tn), "falseNegative": int(fn),
        "recall": round(float(tp / max(tp + fn, 1)), 6), "precision": round(float(tp / max(tp + fp, 1)), 6),
        "falsePositiveRate": round(float(fp / max(fp + tn, 1)), 6),
        "confirmedFraudDetected": int(np.sum(predicted & (labels == 2))), "confirmedFraudTotal": int(np.sum(labels == 2))}


def threshold(labels, probability):
    positive = labels > 0; best = (-1, .5)
    for value in np.linspace(.02, .95, 187):
        m = metrics(labels, probability >= value); score = (m["recall"] * .7 + m["precision"] * .3) - m["falsePositiveRate"]
        if score > best[0]: best = (score, float(value))
    return best[1]


def run_segment(member, bank, sector, rows, seed):
    fraud_count = 3 if sector == "MONETIQUE" else 2
    x, labels = dataset(rows, seed, 50, fraud_count); train_end, validation_end = int(rows * .70), int(rows * .85)
    # The blind labels are not passed to training or threshold selection.
    model = HistGradientBoostingClassifier(max_iter=110, max_leaf_nodes=15, learning_rate=.08,
        min_samples_leaf=5, class_weight="balanced", l2_regularization=.4, random_state=seed)
    weights = np.ones(train_end, dtype=np.float32); weights[labels[:train_end] == 1] = 4; weights[labels[:train_end] == 2] = 40
    model.fit(x[:train_end], labels[:train_end] > 0, sample_weight=weights)
    validation_probability = model.predict_proba(x[train_end:validation_end])[:, 1]
    frozen_threshold = threshold(labels[train_end:validation_end], validation_probability)
    blind_x, blind_labels = x[validation_end:], labels[validation_end:]
    baseline_prediction = baseline(blind_x)
    frozen_probability = model.predict_proba(blind_x)[:, 1]
    ai_prediction = frozen_probability >= frozen_threshold
    # Adaptive discovery runs after the frozen blind result and cannot change it.
    future_x, _ = dataset(min(10000, max(2000, rows // 10)), seed + 9000)
    future_probability = model.predict_proba(future_x)[:, 1]; high = future_x[future_probability >= frozen_threshold]
    proposals = []
    if len(high):
        lift = high.mean(axis=0) - future_x.mean(axis=0)
        for rank, feature_index in enumerate(np.argsort(lift)[::-1][:2], 1):
            proposals.append({"proposalId": f"{member}-{sector}-AUTO-{rank:02d}", "status": "PROPOSED",
                "automaticActivation": False, "feature": FEATURES[int(feature_index)],
                "description": "Adaptive control candidate requiring analyst review."})
    return {"memberId": member, "bank": bank, "sectorId": sector, "rows": rows,
        "knownLabels": {"suspicious": int(np.sum(labels == 1)), "confirmedFraud": int(np.sum(labels == 2))},
        "blindRows": len(blind_labels), "blindLabelsKeptOutOfTraining": True, "modelFrozenBeforeBlindTest": True,
        "phase1Baseline": metrics(blind_labels, baseline_prediction),
        "phase2TrustValidation": {**metrics(blind_labels, ai_prediction), "threshold": round(frozen_threshold, 6)},
        "phase3AdaptiveDiscovery": {"futureRows": len(future_x), "proposals": proposals, "automaticActivation": False}}


def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--rows-per-bank", type=int, default=100000)
    args = parser.parse_args(); rows_per_sector = max(1000, args.rows_per_bank // 2); results = []
    for bank_index, (member, bank) in enumerate(BANKS):
        for sector_index, sector in enumerate(SECTORS):
            results.append(run_segment(member, bank, sector, rows_per_sector, 20260817 + bank_index * 100 + sector_index))
    total_rows = sum(item["rows"] for item in results)
    report = {"status": "TRUST_VALIDATION_LAB_OK", "synthetic": True, "productionClaimAllowed": False,
        "banks": len(BANKS), "sectorsPerBank": len(SECTORS), "totalRows": total_rows,
        "phases": ["INITIAL_CONTROL_BASELINE", "BLIND_TRUST_VALIDATION", "ADAPTIVE_DISCOVERY"], "results": results}
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / "trust-validation-campaign.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    lines = ["# Trust Validation Campaign — laboratoire synthétique", "", f"- Statut : **{report['status']}**", f"- Opérations : **{total_rows:,}**", "- Résultats non utilisables comme revendication de production.", "",
        "| Banque | Secteur | Baseline rappel | IA rappel | Fraudes cachées IA | Propositions |", "|---|---|---:|---:|---:|---:|"]
    for item in results:
        b, a, d = item["phase1Baseline"], item["phase2TrustValidation"], item["phase3AdaptiveDiscovery"]
        lines.append(f"| {item['bank']} | {item['sectorId']} | {b['recall']:.3f} | {a['recall']:.3f} | {a['confirmedFraudDetected']}/{a['confirmedFraudTotal']} | {len(d['proposals'])} |")
    (EVIDENCE / "trust-validation-campaign.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("status", "banks", "sectorsPerBank", "totalRows")}, indent=2))


if __name__ == "__main__": main()
