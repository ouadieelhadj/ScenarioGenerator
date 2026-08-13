package com.staging.sg.way4aura.service;

final class Way4RegNumbers {
    private Way4RegNumbers() {}
    static String client(String root) { return root; }
    static String group(String root) { return root + "-GROUP"; }
    static String chain(String root) { return root + "-CHAIN"; }
    static String account(String root) { return root + "-ACCOUNT"; }
    static String address(String root) { return root + "-ADDRESS"; }
    static String device(String root, int ordinal) {
        if (ordinal < 1 || ordinal > 999)
            throw new IllegalArgumentException("WAY4 device ordinal must be between 1 and 999");
        return root + "-TPE-" + String.format("%03d", ordinal);
    }
}
