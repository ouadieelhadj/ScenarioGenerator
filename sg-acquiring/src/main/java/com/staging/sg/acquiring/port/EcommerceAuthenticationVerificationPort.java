package com.staging.sg.acquiring.port;

import com.staging.sg.common.ecommerce.EcommercePurchaseRequest;

public interface EcommerceAuthenticationVerificationPort {
    boolean verifyAndConsume(EcommercePurchaseRequest request);
}
