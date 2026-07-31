package com.staging.sg.card.issuing.service;

import com.staging.sg.common.issuing.IssuingAuthorizationRequest;
import com.staging.sg.common.issuing.IssuingAuthorizationResponse;

public interface IssuerAuthorizationUseCase {
    IssuingAuthorizationResponse authorize(IssuingAuthorizationRequest request);
}
