package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.api.Way4DryRunRequest;
import com.staging.sg.way4aura.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class Way4MappingService {
    private final AuraBindingResolver resolver;
    private final IdentifierAuthority midAuthority;
    private final IdentifierAuthority tidAuthority;
    public Way4MappingService(AuraBindingResolver resolver,
            @Value("${way4-aura.mid-authority:UNDECIDED}") IdentifierAuthority midAuthority,
            @Value("${way4-aura.tid-authority:UNDECIDED}") IdentifierAuthority tidAuthority) {
        this.resolver = resolver; this.midAuthority = midAuthority; this.tidAuthority = tidAuthority;
    }
    public ResolvedWay4Application resolve(Way4DryRunRequest request) {
        if (request == null || !"1.0".equals(request.schemaVersion()))
            throw new AuraMappingBlockedException("Unsupported connector request schemaVersion");
        if (request.merchant() == null || request.accountContract() == null)
            throw new AuraMappingBlockedException("Merchant and acquiring account contract are required");
        if (midAuthority == IdentifierAuthority.UNDECIDED || tidAuthority == IdentifierAuthority.UNDECIDED)
            throw new AuraMappingBlockedException("MID/TID authority decision is missing");
        if (midAuthority != IdentifierAuthority.FUTURPAYMENT || tidAuthority != IdentifierAuthority.FUTURPAYMENT)
            throw new AuraMappingBlockedException("This dry-run supports only identifiers already allocated by FuturPayment");
        Instant at = Instant.now(); int maxVersion = 0;
        var sender = resolver.required(AuraBindingType.SENDER, "DEFAULT", at); maxVersion = max(maxVersion, sender.version());
        var institution = resolver.required(AuraBindingType.INSTITUTION, "DEFAULT", at); maxVersion = max(maxVersion, institution.version());
        var department = resolver.required(AuraBindingType.ORDER_DEPARTMENT, "DEFAULT", at); maxVersion = max(maxVersion, department.version());
        var clientType = resolver.required(AuraBindingType.CLIENT_TYPE, request.merchant().merchantType(), at); maxVersion = max(maxVersion, clientType.version());
        var clientCategory = resolver.required(AuraBindingType.CLIENT_CATEGORY, "MERCHANT", at); maxVersion = max(maxVersion, clientCategory.version());
        var accountProduct = resolver.required(AuraBindingType.ACCOUNT_PRODUCT, request.accountContract().sourceProductCode(), at); maxVersion = max(maxVersion, accountProduct.version());
        var accountScheme = resolver.required(AuraBindingType.ACCOUNT_SCHEME, request.accountContract().sourceProductCode(), at); maxVersion = max(maxVersion, accountScheme.version());
        var servicePack = resolver.required(AuraBindingType.SERVICE_PACK, request.accountContract().sourceProductCode(), at); maxVersion = max(maxVersion, servicePack.version());
        var addressType = resolver.required(AuraBindingType.PAYMENT_ADDRESS_TYPE, "SETTLEMENT", at); maxVersion = max(maxVersion, addressType.version());
        var accountCurrency = resolver.required(AuraBindingType.CURRENCY, request.accountContract().currencyCode(), at); maxVersion = max(maxVersion, accountCurrency.version());
        var country = resolver.required(AuraBindingType.COUNTRY,
                request.merchant().headquartersAddress().country(), at); maxVersion = max(maxVersion, country.version());
        var devices = request.deviceContracts() == null ? java.util.List.<ResolvedWay4Application.ResolvedDevice>of()
                : request.deviceContracts().stream().map(device -> device(device, at)).toList();
        for (var device : devices) {
            if (device.source().merchantId() == null || device.source().merchantId().isBlank()
                    || device.source().terminalId() == null || device.source().terminalId().isBlank())
                throw new AuraMappingBlockedException("A device MID/TID is missing");
        }
        return new ResolvedWay4Application(sender.code(), institution.code(), department.code(),
                clientType.code(), clientCategory.code(), accountProduct.code(), accountScheme.code(),
                servicePack.code(), addressType.code(), accountCurrency.code(), country.code(), request, devices,
                maxVersion, at);
    }
    private ResolvedWay4Application.ResolvedDevice device(Way4DryRunRequest.DeviceContract source, Instant at) {
        var product = resolver.required(AuraBindingType.POS_PRODUCT, source.sourceProductCode(), at);
        var deviceType = resolver.required(AuraBindingType.DEVICE_TYPE, source.sourceDeviceType(), at);
        var currency = resolver.required(AuraBindingType.CURRENCY, source.currencyCode(), at);
        var sic = resolver.required(AuraBindingType.MCC, source.mcc(), at);
        return new ResolvedWay4Application.ResolvedDevice(source, product.code(),
                deviceType.code(), currency.code(), sic.code());
    }
    private static int max(int left, int right) { return Math.max(left, right); }
}
