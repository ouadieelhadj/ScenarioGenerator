package com.staging.sg.swam.lis.member.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractBusinessDay;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "member_lis_business_day",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_business_date",
                columnNames = {"bank_member_id", "business_date"}))
public class MemberBusinessDay extends AbstractBusinessDay {
}
