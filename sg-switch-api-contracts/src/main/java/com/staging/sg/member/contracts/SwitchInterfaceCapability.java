package com.staging.sg.member.contracts;

public record SwitchInterfaceCapability(boolean registryAvailable, boolean makerCheckerAvailable,
                                        boolean activationAvailable, String reason) { }
