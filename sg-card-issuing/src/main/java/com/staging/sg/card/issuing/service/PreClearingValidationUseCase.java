package com.staging.sg.card.issuing.service;

import com.staging.sg.common.issuing.PreClearingValidationRequest;
import com.staging.sg.common.issuing.PreClearingValidationResponse;

public interface PreClearingValidationUseCase {
    PreClearingValidationResponse validate(PreClearingValidationRequest request);
}
