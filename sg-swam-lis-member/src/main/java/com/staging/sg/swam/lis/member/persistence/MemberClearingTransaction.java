package com.staging.sg.swam.lis.member.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractClearingTransaction;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "member_clearing_transaction", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_clearing_local_source",
                columnNames = {"local_source_type", "local_sid_transaction_id", "clearing_cycle"}),
        @UniqueConstraint(name = "uk_member_clearing_lis_source",
                columnNames = {"incoming_lis_file_id", "incoming_record_sequence"})
})
public class MemberClearingTransaction extends AbstractClearingTransaction {
}
