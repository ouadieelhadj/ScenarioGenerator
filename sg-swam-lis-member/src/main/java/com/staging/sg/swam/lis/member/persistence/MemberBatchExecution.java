package com.staging.sg.swam.lis.member.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractBatchExecution;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "member_lis_batch_execution")
public class MemberBatchExecution extends AbstractBatchExecution {
}
