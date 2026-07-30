package com.staging.sg.waypos.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.domain.PosOutbox;
import com.staging.sg.waypos.server.repository.PosOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PosOutboxDispatcher {
    private static final Logger log =
            LoggerFactory.getLogger(PosOutboxDispatcher.class);
    private final PosOutboxRepository outbox;
    private final WayPosPayloadCipher cipher;
    private final ObjectMapper json;
    private final NetworkRoutingConnector network;
    private final PosJournalService journal;

    public PosOutboxDispatcher(
            PosOutboxRepository outbox, WayPosPayloadCipher cipher,
            ObjectMapper json, NetworkRoutingConnector network,
            PosJournalService journal) {
        this.outbox = outbox;
        this.cipher = cipher;
        this.json = json;
        this.network = network;
        this.journal = journal;
    }

    @Scheduled(fixedDelayString = "${way-pos.outbox-poll-ms:1000}")
    public void dispatchDue() {
        for (PosOutbox item :
                outbox.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderById(
                        "PENDING", Instant.now())) {
            dispatch(item);
        }
    }

    void dispatch(PosOutbox item) {
        try {
            String payload = cipher.decrypt(
                    item.getPayloadCiphertext(), item.getPayloadIv(),
                    item.getPayloadKeyId());
            RoutingTransactionRequest request =
                    json.readValue(payload, RoutingTransactionRequest.class);
            RoutingTransactionResponse response =
                    network.send(item.getDestination(), request);
            if (response.retryable() || "UNKNOWN".equals(response.status())) {
                item.retry();
            } else {
                journal.applyLinkedOutcome(request, response);
                item.delivered(response.posResponseCode());
            }
        } catch (Exception e) {
            item.retry();
            log.warn("[WAY-POS] recovery delivery failed id={} type={} attempt={}",
                    item.getId(), item.getMessageType(), item.getAttempts());
        }
        outbox.save(item);
    }
}
