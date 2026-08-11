package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.api.Way4DryRunRequest;
import com.staging.sg.way4aura.domain.AuraBindingType;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class Way4MappingService {
    private final AuraBindingResolver resolver; private final Way4MidAllocator mids;
    public Way4MappingService(AuraBindingResolver resolver,Way4MidAllocator mids){this.resolver=resolver;this.mids=mids;}
    public ResolvedWay4Application resolve(Way4DryRunRequest request){
        if(request==null||!"2.0".equals(request.schemaVersion()))throw new AuraMappingBlockedException("Unsupported Portal connector schemaVersion");
        if(request.merchant()==null||request.settlement()==null||request.accountProductId()==null)throw new AuraMappingBlockedException("Merchant, settlement and account product are required");
        Instant at=Instant.now();int version=0;
        var sender=resolver.required(AuraBindingType.SENDER,"DEFAULT",at);version=max(version,sender.version());
        var institution=resolver.required(AuraBindingType.INSTITUTION,"DEFAULT",at);version=max(version,institution.version());
        var department=resolver.required(AuraBindingType.ORDER_DEPARTMENT,"DEFAULT",at);version=max(version,department.version());
        var clientType=resolver.required(AuraBindingType.CLIENT_TYPE,request.merchant().merchantType(),at);version=max(version,clientType.version());
        var clientCategory=resolver.required(AuraBindingType.CLIENT_CATEGORY,"MERCHANT",at);version=max(version,clientCategory.version());
        String productKey=request.accountProductId().toString();
        var accountProduct=resolver.required(AuraBindingType.ACCOUNT_PRODUCT,productKey,at);version=max(version,accountProduct.version());
        var accountScheme=resolver.required(AuraBindingType.ACCOUNT_SCHEME,productKey,at);version=max(version,accountScheme.version());
        var servicePack=resolver.required(AuraBindingType.SERVICE_PACK,productKey,at);version=max(version,servicePack.version());
        var addressType=resolver.required(AuraBindingType.PAYMENT_ADDRESS_TYPE,"SETTLEMENT",at);version=max(version,addressType.version());
        var currency=resolver.required(AuraBindingType.CURRENCY,request.settlement().currency(),at);version=max(version,currency.version());
        var country=resolver.required(AuraBindingType.COUNTRY,request.merchant().headquartersAddress().country(),at);version=max(version,country.version());
        List<ResolvedWay4Application.ResolvedDevice> devices=new ArrayList<>();
        for(var outlet:request.outlets()==null?List.<Way4DryRunRequest.Outlet>of():request.outlets())for(var terminal:outlet.terminalRequests()==null?List.<Way4DryRunRequest.TerminalRequest>of():outlet.terminalRequests())for(int ordinal=1;ordinal<=terminal.quantity();ordinal++){
            var product=resolver.required(AuraBindingType.POS_PRODUCT,terminal.productId().toString(),at);
            var type=resolver.required(AuraBindingType.DEVICE_TYPE,terminal.modelCode(),at);
            var sic=resolver.required(AuraBindingType.MCC,request.merchant().mcc(),at);
            int deviceVersion=Math.max(product.version(),Math.max(type.version(),Math.max(sic.version(),currency.version())));version=max(version,deviceVersion);
            devices.add(new ResolvedWay4Application.ResolvedDevice(outlet,terminal,ordinal,
                    Way4RegNumbers.device(request.applicationRegNumber(),terminal.sourceRequestId(),ordinal),
                    product.code(),type.code(),currency.code(),sic.code(),deviceVersion));}
        String mid=mids.allocate(request.onboardingCaseId(),request.applicationRegNumber());
        return new ResolvedWay4Application(sender.code(),institution.code(),department.code(),clientType.code(),clientCategory.code(),
                accountProduct.code(),accountScheme.code(),servicePack.code(),addressType.code(),currency.code(),country.code(),mid,
                request,List.copyOf(devices),version,at);
    }
    private static int max(int a,int b){return Math.max(a,b);}
}
