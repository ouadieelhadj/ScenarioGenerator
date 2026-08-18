from __future__ import annotations

import json
from pathlib import Path

import mlflow
import mlflow.sklearn
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.inspection import permutation_importance
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    precision_recall_fscore_support,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split


ROOT = Path(r"D:\MoneyCore\ScenarioGenerator\fraud-tools-lite")
DATA = ROOT / "data"
EVIDENCE = ROOT / "evidence"
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


class FraudProbabilityModel(mlflow.pyfunc.PythonModel):
    def __init__(self, classifier, alert_threshold: float):
        self.classifier = classifier
        self.alert_threshold = alert_threshold

    def predict(self, context, model_input: pd.DataFrame, params=None):
        probabilities = self.classifier.predict_proba(model_input[FEATURES])[:, 1]
        scores = np.rint(probabilities * 1000).astype(int)
        actions = np.select(
            [probabilities < 0.50, probabilities < 0.70, probabilities < 0.82, probabilities < 0.92],
            ["ALLOW", "ALERT", "CHALLENGE", "HOLD"],
            default="BLOCK",
        )
        return pd.DataFrame(
            {
                "riskScore": scores,
                "riskProbability": probabilities,
                "recommendedAction": actions,
                "alertThreshold": self.alert_threshold,
            }
        )


def generate_dataset(rows: int = 30000, seed: int = 20260816) -> tuple[pd.DataFrame, pd.Series]:
    rng = np.random.default_rng(seed)
    frame = pd.DataFrame(
        {
            "amount_deviation": rng.gamma(1.8, 0.8, rows),
            "attempts_last_hour": rng.poisson(1.8, rows),
            "device_novelty": rng.beta(1.2, 5.0, rows),
            "location_novelty": rng.beta(1.1, 6.0, rows),
            "beneficiary_age_minutes": rng.exponential(1800, rows),
            "graph_group_size": rng.poisson(2.5, rows),
            "behavioral_deviation": rng.beta(1.3, 4.5, rows),
            "threat_intelligence_signal": rng.binomial(1, 0.018, rows),
        }
    )
    # Scenarios injectes : velocite, anomalie comportementale et fraude organisee en graphe.
    latent = (
        1.35 * (frame["amount_deviation"] > 3.2)
        + 1.45 * (frame["attempts_last_hour"] >= 7)
        + 1.25 * (frame["device_novelty"] > 0.65)
        + 1.10 * (frame["location_novelty"] > 0.55)
        + 1.25 * ((frame["beneficiary_age_minutes"] < 20) & (frame["amount_deviation"] > 2.0))
        + 1.40 * (frame["graph_group_size"] >= 8)
        + 1.30 * (frame["behavioral_deviation"] > 0.70)
        + 2.20 * frame["threat_intelligence_signal"]
        + rng.normal(0, 0.48, rows)
    )
    target = (latent >= 2.15).astype(int)
    return frame, pd.Series(target, name="fraud_or_suspicious")


def choose_threshold(y_true: pd.Series, probabilities: np.ndarray) -> float:
    best = None
    for threshold in np.linspace(0.05, 0.95, 181):
        predicted = probabilities >= threshold
        tn, fp, fn, tp = confusion_matrix(y_true, predicted, labels=[0, 1]).ravel()
        recall = tp / max(tp + fn, 1)
        fpr = fp / max(fp + tn, 1)
        precision = tp / max(tp + fp, 1)
        f1 = 2 * precision * recall / max(precision + recall, 1e-12)
        # Le seuil est optimise sur validation, jamais sur le jeu de test final.
        score = (f1, recall, -fpr, -threshold)
        if best is None or score > best[0]:
            best = (score, threshold)
    return float(best[1])


def calculate_metrics(y_true: pd.Series, probabilities: np.ndarray, threshold: float) -> dict:
    predicted = probabilities >= threshold
    tn, fp, fn, tp = confusion_matrix(y_true, predicted, labels=[0, 1]).ravel()
    precision, recall, f1, _ = precision_recall_fscore_support(
        y_true, predicted, average="binary", zero_division=0
    )
    return {
        "roc_auc": float(roc_auc_score(y_true, probabilities)),
        "pr_auc": float(average_precision_score(y_true, probabilities)),
        "precision": float(precision),
        "recall": float(recall),
        "f1": float(f1),
        "false_positive_rate": float(fp / max(fp + tn, 1)),
        "threshold": float(threshold),
        "true_positive": int(tp),
        "false_positive": int(fp),
        "true_negative": int(tn),
        "false_negative": int(fn),
    }


def build_proposals(model, validation_x: pd.DataFrame, validation_y: pd.Series) -> list[dict]:
    importance = permutation_importance(
        model, validation_x, validation_y, n_repeats=4, random_state=20260816, scoring="average_precision"
    )
    ranked = sorted(zip(FEATURES, importance.importances_mean), key=lambda item: item[1], reverse=True)[:4]
    proposals = []
    for position, (feature, feature_importance) in enumerate(ranked, start=1):
        positive_values = validation_x.loc[validation_y == 1, feature]
        threshold = float(positive_values.quantile(0.35))
        matches = validation_x[feature] >= threshold
        tn, fp, fn, tp = confusion_matrix(validation_y, matches, labels=[0, 1]).ravel()
        proposals.append(
            {
                "proposalId": f"AUTO-LAB-{position:03d}",
                "status": "PROPOSED",
                "automaticActivation": False,
                "feature": feature,
                "operator": ">=",
                "threshold": round(threshold, 6),
                "description": f"Controle propose automatiquement sur l'indicateur {feature}.",
                "criticality": "HIGH" if position <= 2 else "MEDIUM",
                "evidence": {
                    "permutationImportance": round(float(feature_importance), 6),
                    "support": int(matches.sum()),
                    "precision": round(float(tp / max(tp + fp, 1)), 6),
                    "recall": round(float(tp / max(tp + fn, 1)), 6),
                    "falsePositiveRate": round(float(fp / max(fp + tn, 1)), 6),
                },
                "governance": {
                    "requiredDecision": "APPROVE_OR_REJECT_BY_ANALYST",
                    "allowedLifecycle": ["PROPOSED", "BACKTESTED", "APPROVED", "ACTIVE", "DISABLED"],
                    "rollbackSupported": True,
                },
            }
        )
    return proposals


def main() -> None:
    DATA.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    frame, target = generate_dataset()
    train_x, holdout_x, train_y, holdout_y = train_test_split(
        frame, target, test_size=0.40, random_state=20260816, stratify=target
    )
    validation_x, test_x, validation_y, test_y = train_test_split(
        holdout_x, holdout_y, test_size=0.50, random_state=20260816, stratify=holdout_y
    )
    model = HistGradientBoostingClassifier(
        learning_rate=0.08,
        max_iter=140,
        max_leaf_nodes=15,
        min_samples_leaf=30,
        l2_regularization=0.4,
        class_weight="balanced",
        random_state=20260816,
    )
    model.fit(train_x, train_y)
    validation_probabilities = model.predict_proba(validation_x)[:, 1]
    threshold = choose_threshold(validation_y, validation_probabilities)
    test_probabilities = model.predict_proba(test_x)[:, 1]
    metrics = calculate_metrics(test_y, test_probabilities, threshold)

    database_uri = "sqlite:///" + (DATA / "mlflow.db").as_posix()
    artifacts = (DATA / "mlflow-artifacts").resolve()
    artifacts.mkdir(parents=True, exist_ok=True)
    mlflow.set_tracking_uri(database_uri)
    experiment_name = "futurpayment-fraud-lite"
    experiment = mlflow.get_experiment_by_name(experiment_name)
    if experiment is None:
        mlflow.create_experiment(experiment_name, artifact_location=artifacts.as_uri())
    mlflow.set_experiment(experiment_name)
    with mlflow.start_run(run_name="hist-gradient-boosting-lite") as run:
        mlflow.log_params({"rows": len(frame), "model": "HistGradientBoostingClassifier", "seed": 20260816})
        mlflow.log_metrics(metrics)
        mlflow.pyfunc.log_model(
            name="model",
            python_model=FraudProbabilityModel(model, threshold),
            input_example=train_x.head(3),
            pip_requirements=["mlflow==3.14.0", "scikit-learn==1.9.0", "pandas==2.3.3", "numpy==2.5.2"],
        )
        run_id = run.info.run_id

    proposals = build_proposals(model, validation_x, validation_y)
    summary = {
        "status": "AI_LAB_OK",
        "trackingUri": database_uri,
        "experiment": experiment_name,
        "runId": run_id,
        "modelUri": f"runs:/{run_id}/model",
        "dataset": {
            "rows": int(len(frame)),
            "positiveCases": int(target.sum()),
            "trainRows": int(len(train_x)),
            "validationRows": int(len(validation_x)),
            "testRows": int(len(test_x)),
            "synthetic": True,
        },
        "metricsOnUntouchedTestSet": metrics,
        "proposalCount": len(proposals),
        "productionClaimAllowed": False,
    }
    (EVIDENCE / "ai-training-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (EVIDENCE / "ai-control-proposals.json").write_text(json.dumps(proposals, indent=2), encoding="utf-8")
    sample = test_x.head(1).copy()
    sample["score"] = test_probabilities[:1]
    sample["decision"] = np.where(test_probabilities[:1] >= threshold, "ALERT", "ALLOW")
    (EVIDENCE / "ai-scoring-sample.json").write_text(
        sample.to_json(orient="records", indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
