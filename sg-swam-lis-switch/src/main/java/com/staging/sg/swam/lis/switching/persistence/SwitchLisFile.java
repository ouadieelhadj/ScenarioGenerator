package com.staging.sg.swam.lis.switching.persistence;

import com.staging.sg.swam.lis.common.persistence.AbstractLisFile;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "switch_lis_file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_switch_lis_file_identity", columnNames = {
                "direction", "source_member", "destination_member",
                "processing_date", "file_sequence", "regeneration_status"
        })
})
public class SwitchLisFile extends AbstractLisFile {
}
