package com.staging.sg.swam.lis.switching.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractBatchExecution;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "switch_lis_batch_execution")
public class SwitchBatchExecution extends AbstractBatchExecution {
}
