from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import pandas as pd
from feast import FeatureStore

root = Path(__file__).resolve().parents[1]
repo = root / "feature-repo"
data = repo / "data" / "fraud_features.parquet"
now = datetime.now(timezone.utc)
pd.DataFrame([
    {
        "instrument_id": "tok_bank_lab_001",
        "attempts_last_hour": 8,
        "amount_deviation": 4.2,
        "device_novelty": 1.0,
        "graph_group_size": 12,
        "event_timestamp": now - timedelta(minutes=1),
    }
]).to_parquet(data, index=False)

store = FeatureStore(repo_path=str(repo))
store.apply([]) if False else None
store.materialize_incremental(end_date=now + timedelta(minutes=1))
result = store.get_online_features(
    features=[
        "fraud_transaction_features:attempts_last_hour",
        "fraud_transaction_features:amount_deviation",
        "fraud_transaction_features:device_novelty",
        "fraud_transaction_features:graph_group_size",
    ],
    entity_rows=[{"instrument_id": "tok_bank_lab_001"}],
).to_dict()
print(json.dumps(result, sort_keys=True))
assert result["attempts_last_hour"][0] == 8
assert result["graph_group_size"][0] == 12
