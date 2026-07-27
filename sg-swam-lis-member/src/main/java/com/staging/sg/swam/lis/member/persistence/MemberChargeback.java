package com.staging.sg.swam.lis.member.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractChargeback;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "member_chargeback", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_chargeback_lis_source",
                columnNames = {"source_lis_file_id", "source_record_sequence"}),
        @UniqueConstraint(name = "uk_member_chargeback_business",
                columnNames = {"clearing_transaction_id", "direction", "cycle_number",
                        "reason_code", "chargeback_reference"})
})
public class MemberChargeback extends AbstractChargeback {
}
