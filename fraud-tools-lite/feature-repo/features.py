from datetime import timedelta
from feast import Entity, FeatureView, Field, FileSource, ValueType
from feast.types import Float32, Int64

instrument = Entity(
    name="instrument",
    join_keys=["instrument_id"],
    value_type=ValueType.STRING,
)

fraud_features_source = FileSource(
    name="fraud_features_source",
    path="data/fraud_features.parquet",
    timestamp_field="event_timestamp",
)

fraud_transaction_features = FeatureView(
    name="fraud_transaction_features",
    entities=[instrument],
    ttl=timedelta(days=30),
    schema=[
        Field(name="attempts_last_hour", dtype=Int64),
        Field(name="amount_deviation", dtype=Float32),
        Field(name="device_novelty", dtype=Float32),
        Field(name="graph_group_size", dtype=Int64),
    ],
    source=fraud_features_source,
    online=True,
)
