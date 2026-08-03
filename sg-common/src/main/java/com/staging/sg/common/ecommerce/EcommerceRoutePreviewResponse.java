package com.staging.sg.common.ecommerce;

import com.staging.sg.common.threeds.ThreeDsIssuerMode;
import com.staging.sg.common.threeds.ThreeDsProgram;

public record EcommerceRoutePreviewResponse(
        EcommerceNetworkRoute networkRoute,
        ThreeDsProgram threeDsProgram,
        ThreeDsIssuerMode issuerMode) {
}
