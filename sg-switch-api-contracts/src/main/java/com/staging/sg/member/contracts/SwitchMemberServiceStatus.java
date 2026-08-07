package com.staging.sg.member.contracts;

import java.util.List;

public record SwitchMemberServiceStatus(
        String code,
        String label,
        boolean configured,
        String status,
        List<String> capabilities,
        String limitation) {
}
