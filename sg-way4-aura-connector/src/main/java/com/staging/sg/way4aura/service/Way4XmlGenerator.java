package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.api.Way4DryRunRequest;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.List;

@Component
public class Way4XmlGenerator {
    public byte[] generate(ResolvedWay4Application resolved, long fileNumber, Instant generatedAt) {
        return generate(List.of(resolved), fileNumber, generatedAt);
    }

    public byte[] generate(List<ResolvedWay4Application> resolvedApplications, long fileNumber,
            Instant generatedAt) {
        if (resolvedApplications == null || resolvedApplications.isEmpty())
            throw new IllegalArgumentException("At least one WAY4 application is required");
        ResolvedWay4Application headerValues = resolvedApplications.get(0);
        boolean inconsistentHeader = resolvedApplications.stream().anyMatch(value ->
                !headerValues.sender().equals(value.sender())
                        || !headerValues.institution().equals(value.institution()));
        if (inconsistentHeader)
            throw new IllegalArgumentException("All applications in a WAY4 file must share sender and institution");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().newDocument();
            Element root = element(document, document, "ApplicationFile", null);
            Element header = element(document, root, "FileHeader", null);
            element(document, header, "FormatVersion", "2.0");
            element(document, header, "Sender", headerValues.sender());
            ZonedDateTime time = generatedAt.atZone(ZoneOffset.UTC);
            element(document, header, "CreationDate", time.toLocalDate().toString());
            element(document, header, "CreationTime", time.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            element(document, header, "Number", String.format("%05d", fileNumber));
            element(document, header, "Institution", headerValues.institution());
            Element list = element(document, root, "ApplicationsList", null);
            resolvedApplications.forEach(resolved -> clientApplication(document, list, resolved));
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot generate WAY4 ApplicationFile", exception);
        }
    }

    private void clientApplication(Document doc, Element list, ResolvedWay4Application r) {
        Way4DryRunRequest request = r.request(); Way4DryRunRequest.Merchant merchant = request.merchant();
        Element application = application(doc, list, Way4RegNumbers.client(request.applicationRegNumber()), r, "Client");
        Element data = element(doc, application, "Data", null);
        Element client = element(doc, data, "Client", null);
        element(doc, client, "ClientType", r.clientType());
        element(doc, client, "ClientCategory", r.clientCategory());
        Element info = element(doc, client, "ClientInfo", null);
        element(doc, info, "RegNumber", merchant.registrationNumber());
        element(doc, info, "ShortName", merchant.tradingName());
        if (merchant.taxIdentifier() != null && !merchant.taxIdentifier().isBlank())
            element(doc, info, "TaxpayerIdentifier", merchant.taxIdentifier());
        element(doc, info, "CompanyName", merchant.legalName());
        element(doc, info, "CompanyTradeName", merchant.tradingName());
        address(doc, client, "BaseAddress", merchant.headquartersAddress(), null, r.headquartersCountry());
        Element sub = element(doc, application, "SubApplList", null);
        groupApplication(doc, sub, r);
    }

    private void groupApplication(Document doc, Element parent, ResolvedWay4Application r) {
        Way4DryRunRequest request = r.request();
        Element application = application(doc, parent, Way4RegNumbers.group(request.applicationRegNumber()), r, "Contract");
        Element data = element(doc, application, "Data", null);
        Element contract = element(doc, data, "Contract", null);
        element(doc, contract, "ContractName", request.merchant().legalName());
        Element product = element(doc, contract, "Product", null);
        element(doc, product, "ProductCode1", r.groupProduct());
        Element sub = element(doc, application, "SubApplList", null);
        Element addressApp = application(doc, sub, Way4RegNumbers.address(request.applicationRegNumber()), r, "ContractAddress");
        Element addressData = element(doc, addressApp, "Data", null);
        address(doc, addressData, "Address", request.merchant().headquartersAddress(),
                r.paymentAddressType(), r.headquartersCountry());
        chainApplication(doc, sub, r);
    }

    private void chainApplication(Document doc, Element parent, ResolvedWay4Application r) {
        Way4DryRunRequest request = r.request();
        Element application = application(doc, parent, Way4RegNumbers.chain(request.applicationRegNumber()), r, "Contract");
        Element data = element(doc, application, "Data", null);
        Element contract = element(doc, data, "Contract", null);
        contractId(doc, contract, r.merchantContractNumber());
        element(doc, contract, "ContractName", request.merchant().tradingName());
        Element product = element(doc, contract, "Product", null);
        element(doc, product, "ProductCode1", r.chainProduct());
        Element sub = element(doc, application, "SubApplList", null);
        outletApplication(doc, sub, r);
    }

    private void outletApplication(Document doc, Element parent, ResolvedWay4Application r) {
        Way4DryRunRequest request = r.request();
        Element application = application(doc, parent, Way4RegNumbers.account(request.applicationRegNumber()), r, "Contract");
        Element data = element(doc, application, "Data", null);
        Element contract = element(doc, data, "Contract", null);
        element(doc, contract, "ContractName", request.outlets().get(0).name());
        Element product = element(doc, contract, "Product", null);
        element(doc, product, "ProductCode1", r.accountProduct());
        Element sub = element(doc, application, "SubApplList", null);
        for (ResolvedWay4Application.ResolvedDevice device : r.devices()) deviceApplication(doc, sub, r, device);
    }

    private void deviceApplication(Document doc, Element parent, ResolvedWay4Application r,
            ResolvedWay4Application.ResolvedDevice device) {
        var source = device.source();
        Element application = application(doc, parent, device.applicationRegNumber(), r, "Contract");
        Element data = element(doc, application, "Data", null);
        Element contract = element(doc, data, "Contract", null);
        contractId(doc, contract, device.tid());
        element(doc, contract, "ContractName", device.outlet().code() + " - "
                + device.outlet().name() + " - TPE " + String.format("%03d", device.ordinal()));
        Element product = element(doc, contract, "Product", null);
        element(doc, product, "ProductCode1", device.product());
        Element deviceInfo = element(doc, contract, "DeviceInfo", null);
        element(doc, deviceInfo, "SIC", device.sic());
        element(doc, deviceInfo, "MerchantID", device.mid());
        Element record = element(doc, deviceInfo, "DeviceRecord", null);
        element(doc, record, "DeviceType", device.deviceType());
        element(doc, record, "Location", device.outlet().address().line1());
        element(doc, record, "DefaultCurr", device.currency());
        Element config = element(doc, record, "DeviceConfig", null);
        element(doc, config, "Status", "NotConfigured");
    }
    private void contractId(Document doc, Element contract, String number) {
        Element idt = element(doc, contract, "ContractIDT", null);
        element(doc, idt, "ContractNumber", number);
    }

    private Element application(Document doc, Element parent, String regNumber,
            ResolvedWay4Application r, String objectType) {
        Element app = element(doc, parent, "Application", null);
        element(doc, app, "RegNumber", regNumber);
        element(doc, app, "Institution", r.institution());
        element(doc, app, "OrderDprt", r.orderDepartment());
        element(doc, app, "ObjectType", objectType);
        element(doc, app, "ActionType", "Add");
        element(doc, app, "ProductCategory", "Acquiring");
        return app;
    }
    private void address(Document doc, Element parent, String name, Way4DryRunRequest.Address source,
            String addressType, String resolvedCountry) {
        Element address = element(doc, parent, name, null);
        if (addressType != null) element(doc, address, "AddressType", addressType);
        element(doc, address, "Country", resolvedCountry); element(doc, address, "City", source.city());
        if (source.postalCode() != null) element(doc, address, "PostalCode", source.postalCode());
        if (addressType != null) element(doc, address, "AddressLocation", source.city());
        element(doc, address, "AddressLine1", source.line1());
    }
    private static Element element(Document doc, Node parent, String name, String value) {
        Element result = doc.createElement(name); if (value != null) result.setTextContent(value);
        parent.appendChild(result); return result;
    }
}
