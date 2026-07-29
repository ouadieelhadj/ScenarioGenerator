package com.staging.sg.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "mc_dmas_issuer_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mc_dmas_issuer_tx_network",
                columnNames = {"bank_code", "de011_stan", "de007_transmission_datetime"}))
public class McDmasIssuerTransaction extends AbstractMcDmasAuthorizationTransaction {
}
