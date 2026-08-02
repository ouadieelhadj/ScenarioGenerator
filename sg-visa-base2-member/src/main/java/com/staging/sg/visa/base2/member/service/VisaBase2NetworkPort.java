package com.staging.sg.visa.base2.member.service;

import com.staging.sg.visa.base2.common.*;

public interface VisaBase2NetworkPort {
    VisaBase2NetworkAck send(VisaBase2NetworkFileEnvelope envelope);
}
