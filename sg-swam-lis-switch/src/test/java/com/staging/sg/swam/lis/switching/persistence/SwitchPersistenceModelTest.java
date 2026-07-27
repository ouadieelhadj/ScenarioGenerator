package com.staging.sg.swam.lis.switching.persistence;

import com.staging.sg.swam.lis.common.model.*;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchPersistenceModelTest {
    @Test
    void usesDedicatedSwitchTables() {
        assertThat(tableName(SwitchBusinessDay.class)).isEqualTo("switch_lis_business_day");
        assertThat(tableName(SwitchBatchExecution.class)).isEqualTo("switch_lis_batch_execution");
        assertThat(tableName(SwitchLisFile.class)).isEqualTo("switch_lis_file");
        assertThat(tableName(SwitchClearingTransaction.class)).isEqualTo("switch_clearing_transaction");
        assertThat(tableName(SwitchChargeback.class)).isEqualTo("switch_chargeback");
    }

    @Test
    void clearingAndChargebackDefaultsAreSafe() {
        SwitchClearingTransaction transaction = new SwitchClearingTransaction();
        assertThat(transaction.getClearingCycle()).isEqualTo(1);
        assertThat(transaction.getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
        assertThat(transaction.getAccountingStatus()).isEqualTo(AccountingStatus.PENDING);
        assertThat(transaction.getDisputeStatus()).isEqualTo(DisputeStatus.NONE);

        SwitchChargeback chargeback = new SwitchChargeback();
        assertThat(chargeback.getCycleNumber()).isEqualTo(1);
        assertThat(chargeback.getStatus()).isEqualTo(ChargebackStatus.DRAFT);
    }

    private String tableName(Class<?> type) {
        return type.getAnnotation(Table.class).name();
    }
}
