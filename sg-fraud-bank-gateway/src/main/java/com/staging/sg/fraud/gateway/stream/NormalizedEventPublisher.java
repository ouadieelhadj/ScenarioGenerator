package com.staging.sg.fraud.gateway.stream;

import com.staging.sg.fraud.gateway.api.OmnichannelApi.UniversalTransactionRequest;

/** Stable port implemented by Kafka/Strimzi after the broker is installed. */
public interface NormalizedEventPublisher {
    Publication publish(String memberId,UniversalTransactionRequest event);
    record Publication(String eventId,String status) {}
}
