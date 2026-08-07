package com.staging.sg.member.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchInterfaceDefinition(String id, String code, String name, String bankCode,
                                        String network, String protocol, String messageFormat,
                                        String host, int port, int priority, String failoverInterfaceCode,
                                        String certificateReference, String keyReference,
                                        String status, String connectionStatus,
                                        List<String> allowedActions, Instant updatedAt) { }
