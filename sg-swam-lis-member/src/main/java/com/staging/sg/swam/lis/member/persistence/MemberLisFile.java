package com.staging.sg.swam.lis.member.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractLisFile;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "member_lis_file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_lis_file_identity", columnNames = {
                "direction", "source_member", "destination_member",
                "processing_date", "file_sequence", "regeneration_status"
        })
})
public class MemberLisFile extends AbstractLisFile {
}
