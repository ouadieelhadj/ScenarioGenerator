from datetime import datetime, timedelta, timezone
from pathlib import Path
import pandas as pd

root = Path(__file__).resolve().parents[1]
target = root / "feature-repo" / "data" / "fraud_features.parquet"
now = datetime.now(timezone.utc)
pd.DataFrame([{
    "instrument_id": "tok_bank_lab_001",
    "attempts_last_hour": 8,
    "amount_deviation": 4.2,
    "device_novelty": 1.0,
    "graph_group_size": 12,
    "event_timestamp": now - timedelta(minutes=1),
}]).to_parquet(target, index=False)
print(target)
