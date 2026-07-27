package com.staging.sg.swam.lis.switching.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractClearingTransaction;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "switch_clearing_transaction", uniqueConstraints = {
        @UniqueConstraint(name = "uk_switch_clearing_local_source",
                columnNames = {"local_source_type", "local_sid_transaction_id", "clearing_cycle"}),
        @UniqueConstraint(name = "uk_switch_clearing_lis_source",
                columnNames = {"incoming_lis_file_id", "incoming_record_sequence"})
})
public class SwitchClearingTransaction extends AbstractClearingTransaction {
}
