package com.staging.sg.acquiring.port;

import com.staging.sg.common.routing.RoutingTransactionResponse;

public interface EcommerceNetworkPort {
    RoutingTransactionResponse authorize(EcommerceNetworkCommand command);
}
