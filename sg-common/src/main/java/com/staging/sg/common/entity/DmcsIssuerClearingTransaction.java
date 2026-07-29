package com.staging.sg.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "dmcs_issuer_clearing_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dmcs_iss_clearing_local",
                columnNames = {"source_type", "local_authorization_id", "lifecycle_stage"}))
public class DmcsIssuerClearingTransaction extends AbstractDmcClearingTransaction {
}
