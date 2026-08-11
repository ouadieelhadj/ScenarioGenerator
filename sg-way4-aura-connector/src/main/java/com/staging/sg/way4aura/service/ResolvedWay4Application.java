package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.api.Way4DryRunRequest;
import java.time.Instant;
import java.util.List;

public record ResolvedWay4Application(String sender,String institution,String orderDepartment,
        String clientType,String clientCategory,String accountProduct,String accountScheme,String servicePack,
        String paymentAddressType,String accountCurrency,String headquartersCountry,String mid,
        Way4DryRunRequest request,List<ResolvedDevice> devices,int mappingVersion,Instant resolvedAt){
    public record ResolvedDevice(Way4DryRunRequest.Outlet outlet,Way4DryRunRequest.TerminalRequest source,
            int ordinal,String applicationRegNumber,String product,String deviceType,String currency,String sic,int mappingVersion){}
}
