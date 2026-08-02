package com.staging.sg.visa.online.member.service;

import com.staging.sg.visa.common.online.VisaOnlineNetworkEnvelope;

public interface VisaNetTransport {
    VisaOnlineNetworkEnvelope exchange(VisaOnlineNetworkEnvelope envelope);
}
