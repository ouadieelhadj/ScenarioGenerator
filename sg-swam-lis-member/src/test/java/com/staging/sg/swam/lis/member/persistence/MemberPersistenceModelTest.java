package com.staging.sg.swam.lis.member.persistence;

import com.staging.sg.swam.lis.common.model.*;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberPersistenceModelTest {
    @Test
    void usesDedicatedMemberTables() {
        assertThat(tableName(MemberBusinessDay.class)).isEqualTo("member_lis_business_day");
        assertThat(tableName(MemberBatchExecution.class)).isEqualTo("member_lis_batch_execution");
        assertThat(tableName(MemberLisFile.class)).isEqualTo("member_lis_file");
        assertThat(tableName(MemberClearingTransaction.class)).isEqualTo("member_clearing_transaction");
        assertThat(tableName(MemberChargeback.class)).isEqualTo("member_chargeback");
    }

    @Test
    void clearingAndChargebackDefaultsAreSafe() {
        MemberClearingTransaction transaction = new MemberClearingTransaction();
        assertThat(transaction.getClearingCycle()).isEqualTo(1);
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
        assertThat(transaction.getAccountingStatus()).isEqualTo(AccountingStatus.PENDING);
        assertThat(transaction.getDisputeStatus()).isEqualTo(DisputeStatus.NONE);

        MemberChargeback chargeback = new MemberChargeback();
        assertThat(chargeback.getCycleNumber()).isEqualTo(1);
        assertThat(chargeback.getStatus()).isEqualTo(ChargebackStatus.DRAFT);
    }

    private String tableName(Class<?> type) {
        return type.getAnnotation(Table.class).name();
    }
}
