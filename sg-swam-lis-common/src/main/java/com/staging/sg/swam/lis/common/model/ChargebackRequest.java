package com.staging.sg.swam.lis.common.model;
import jakarta.validation.constraints.*;
public record ChargebackRequest(@NotNull Long clearingTransactionId,
        @Pattern(regexp="\\d{4}") String reasonCode, @Positive long amount,
        @Pattern(regexp="\\d{3}") String currency, @NotBlank String counterpartyMember,
        @NotBlank String createdBy, String manualReason) {}
