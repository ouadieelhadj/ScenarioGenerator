package com.staging.sg.member.contracts;

public record SwitchInterfaceRequest(String code, String name, String bankCode, String network,
                                     String protocol, String messageFormat, String host, int port,
                                     int priority, String failoverInterfaceCode,
                                     String certificateReference, String keyReference) { }
