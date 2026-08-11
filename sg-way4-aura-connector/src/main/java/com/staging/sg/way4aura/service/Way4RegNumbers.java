package com.staging.sg.way4aura.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class Way4RegNumbers {
    private Way4RegNumbers() {}
    static String client(String root) { return root; }
    static String account(String root) { return root + "-ACCOUNT"; }
    static String address(String root) { return root + "-ADDRESS"; }
    static String device(String root, UUID requestId, int ordinal) {
        UUID stable=UUID.nameUUIDFromBytes((requestId+":"+ordinal).getBytes(StandardCharsets.UTF_8));
        return root+"-TPE-"+stable.toString().replace("-","").substring(0,16).toUpperCase();
    }
}
