package com.staging.sg.card.issuing.integration.corebanking.sandbox;

import com.staging.sg.card.issuing.integration.corebanking.CoreBankingAuthorizationRequest;
import com.staging.sg.card.issuing.integration.corebanking.CoreBankingAuthorizationResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "issuing.core-banking.sandbox.enabled",
        havingValue = "true")
public class CoreBankingSandboxService {
    private final JdbcTemplate database;

    public CoreBankingSandboxService(JdbcTemplate database) {
        this.database = database;
    }

    @Transactional
    public void putAccount(
            String fundingContractId,
            CoreBankingSandboxAccountRequest request) {
        if (blank(fundingContractId) || request == null
                || blank(request.issuerId())
                || request.currency() == null
                || !request.currency().matches("[A-Z0-9]{3}")
                || request.availableBalanceMinor() < 0
                || (!"ACTIVE".equals(request.status())
                && !"BLOCKED".equals(request.status()))) {
            throw new IllegalArgumentException(
                    "Invalid Core Banking sandbox account");
        }
        database.update("""
                INSERT INTO issuing_core_banking_sandbox_account
                    (issuer_id, funding_contract_id, currency,
                     available_balance_minor, status, updated_at, row_version)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (issuer_id, funding_contract_id)
                DO UPDATE SET currency = EXCLUDED.currency,
                    available_balance_minor = EXCLUDED.available_balance_minor,
                    status = EXCLUDED.status,
                    updated_at = CURRENT_TIMESTAMP,
                    row_version =
                        issuing_core_banking_sandbox_account.row_version + 1
                """, request.issuerId(), fundingContractId, request.currency(),
                request.availableBalanceMinor(), request.status());
    }

    @Transactional
    public CoreBankingAuthorizationResponse authorize(
            CoreBankingAuthorizationRequest request) {
        validate(request);
        String fingerprint = fingerprint(request);
        List<JournalRow> replay = database.query("""
                        SELECT request_fingerprint, response_status,
                               response_code, approved_amount_minor,
                               funding_reference
                          FROM issuing_core_banking_sandbox_authorization
                         WHERE issuer_id = ? AND idempotency_key = ?
                        """,
                (rs, row) -> new JournalRow(
                        rs.getString("request_fingerprint"),
                        CoreBankingAuthorizationResponse.Status.valueOf(
                                rs.getString("response_status")),
                        rs.getString("response_code"),
                        rs.getLong("approved_amount_minor"),
                        rs.getString("funding_reference")),
                request.issuerId(), request.idempotencyKey());
        if (!replay.isEmpty()) {
            JournalRow prior = replay.getFirst();
            if (!prior.fingerprint().equals(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key reused with a different request");
            }
            return response(request, prior.status(), prior.responseCode(),
                    prior.approvedAmountMinor(), prior.fundingReference());
        }

        List<AccountRow> accounts = database.query("""
                        SELECT currency, available_balance_minor, status
                          FROM issuing_core_banking_sandbox_account
                         WHERE issuer_id = ? AND funding_contract_id = ?
                         FOR UPDATE
                        """,
                (rs, row) -> new AccountRow(
                        rs.getString("currency"),
                        rs.getLong("available_balance_minor"),
                        rs.getString("status")),
                request.issuerId(), request.fundingContractId());

        CoreBankingAuthorizationResponse.Status status;
        String code;
        long approved = 0;
        String reference = null;
        if (accounts.isEmpty()) {
            status = CoreBankingAuthorizationResponse.Status.DECLINED;
            code = "ACCOUNT_NOT_FOUND";
        } else {
            AccountRow account = accounts.getFirst();
            if (!"ACTIVE".equals(account.status())) {
                status = CoreBankingAuthorizationResponse.Status.DECLINED;
                code = "ACCOUNT_NOT_ACTIVE";
            } else if (!request.currency().equals(account.currency())) {
                status = CoreBankingAuthorizationResponse.Status.DECLINED;
                code = "CURRENCY_NOT_ALLOWED";
            } else if (account.availableBalanceMinor() < request.amountMinor()) {
                status = CoreBankingAuthorizationResponse.Status.DECLINED;
                code = "INSUFFICIENT_FUNDS";
            } else {
                database.update("""
                                UPDATE issuing_core_banking_sandbox_account
                                   SET available_balance_minor =
                                           available_balance_minor - ?,
                                       updated_at = CURRENT_TIMESTAMP,
                                       row_version = row_version + 1
                                 WHERE issuer_id = ?
                                   AND funding_contract_id = ?
                                """,
                        request.amountMinor(), request.issuerId(),
                        request.fundingContractId());
                status = CoreBankingAuthorizationResponse.Status.APPROVED;
                code = "APPROVED";
                approved = request.amountMinor();
                reference = "CBS-" + UUID.randomUUID();
            }
        }
        try {
            database.update("""
                            INSERT INTO
                                issuing_core_banking_sandbox_authorization
                                (id, issuer_id, idempotency_key,
                                 request_fingerprint, transaction_id,
                                 correlation_id, funding_contract_id,
                                 amount_minor, currency, response_status,
                                 response_code, approved_amount_minor,
                                 funding_reference, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                    CURRENT_TIMESTAMP)
                            """,
                    UUID.randomUUID(), request.issuerId(),
                    request.idempotencyKey(), fingerprint,
                    request.transactionId(), request.correlationId(),
                    request.fundingContractId(), request.amountMinor(),
                    request.currency(), status.name(), code, approved, reference);
        } catch (DuplicateKeyException race) {
            throw new IllegalStateException(
                    "Concurrent idempotent request must be retried", race);
        }
        return response(request, status, code, approved, reference);
    }

    private static CoreBankingAuthorizationResponse response(
            CoreBankingAuthorizationRequest request,
            CoreBankingAuthorizationResponse.Status status,
            String code, long approved, String reference) {
        return new CoreBankingAuthorizationResponse(
                "1.0", request.issuerId(), request.transactionId(),
                request.correlationId(), status, code, approved, reference);
    }

    private static void validate(CoreBankingAuthorizationRequest request) {
        if (request == null || blank(request.issuerId())
                || blank(request.fundingContractId())
                || request.operation() == null || request.amountMinor() <= 0
                || request.currency() == null
                || !request.currency().matches("[A-Z0-9]{3}")
                || blank(request.transactionId())
                || blank(request.correlationId())
                || blank(request.idempotencyKey())) {
            throw new IllegalArgumentException(
                    "Invalid Core Banking authorization request");
        }
    }

    private static String fingerprint(
            CoreBankingAuthorizationRequest request) {
        String canonical = String.join("\u001f",
                request.issuerId(), request.fundingContractId(),
                request.operation().name(),
                Long.toString(request.amountMinor()), request.currency(),
                request.transactionId(),
                nullToEmpty(request.originalTransactionId()),
                request.correlationId());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record AccountRow(
            String currency, long availableBalanceMinor, String status) {
    }

    private record JournalRow(
            String fingerprint,
            CoreBankingAuthorizationResponse.Status status,
            String responseCode,
            long approvedAmountMinor,
            String fundingReference) {
    }
}
