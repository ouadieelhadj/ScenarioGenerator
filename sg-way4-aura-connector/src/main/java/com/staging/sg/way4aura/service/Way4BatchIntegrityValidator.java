package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.api.Way4DryRunRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class Way4BatchIntegrityValidator {
    private static final Pattern FORBIDDEN = Pattern.compile("(?i)(FIRST|SECOND|\\bTEST\\b)");
    private final int expectedMerchants;
    private final int expectedTerminals;

    public Way4BatchIntegrityValidator(
            @Value("${way4-aura.batch.expected-merchants:0}") int expectedMerchants,
            @Value("${way4-aura.batch.expected-terminals:0}") int expectedTerminals) {
        this.expectedMerchants = expectedMerchants;
        this.expectedTerminals = expectedTerminals;
    }

    public void validateSources(List<Way4DryRunRequest> requests) {
        if (expectedMerchants > 0 && requests.size() != expectedMerchants)
            throw new IllegalArgumentException("WAY4 batch must contain exactly " + expectedMerchants + " merchants");
        int terminals = requests.stream().mapToInt(this::terminalCount).sum();
        if (expectedTerminals > 0 && terminals != expectedTerminals)
            throw new IllegalArgumentException("WAY4 batch must contain exactly " + expectedTerminals + " terminals");
        Set<String> regNumbers = new HashSet<>();
        for (Way4DryRunRequest request : requests) {
            requireUnique(regNumbers, Way4RegNumbers.client(request.applicationRegNumber()));
            requireUnique(regNumbers, Way4RegNumbers.group(request.applicationRegNumber()));
            requireUnique(regNumbers, Way4RegNumbers.chain(request.applicationRegNumber()));
            requireUnique(regNumbers, Way4RegNumbers.account(request.applicationRegNumber()));
            requireUnique(regNumbers, Way4RegNumbers.address(request.applicationRegNumber()));
            if (FORBIDDEN.matcher(String.valueOf(request)).find())
                throw new IllegalArgumentException("WAY4 source contains a forbidden demonstration value");
            if (request.merchant() == null || request.merchant().headquartersAddress() == null
                    || request.settlement() == null || request.outlets() == null || request.outlets().isEmpty())
                throw new IllegalArgumentException("Incomplete merchant source for WAY4 generation");
            int ordinal = 0;
            for (Way4DryRunRequest.Outlet outlet : request.outlets()) {
                if (outlet.code() == null || outlet.code().isBlank() || outlet.name() == null || outlet.name().isBlank()
                        || outlet.address() == null || outlet.address().line1() == null || outlet.address().line1().isBlank())
                    throw new IllegalArgumentException("Incomplete outlet source for WAY4 generation");
                if (outlet.terminalRequests() == null) continue;
                for (Way4DryRunRequest.TerminalRequest terminal : outlet.terminalRequests()) {
                    if (terminal.quantity() < 1) throw new IllegalArgumentException("Terminal quantity must be positive");
                    for (int index = 0; index < terminal.quantity(); index++)
                        requireUnique(regNumbers, Way4RegNumbers.device(request.applicationRegNumber(), ++ordinal));
                }
            }
        }
    }

    public void validateGeneratedXml(byte[] xml, List<Way4DryRunRequest> requests) {
        try {
            String text = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
            if (FORBIDDEN.matcher(text).find())
                throw new IllegalArgumentException("Generated WAY4 XML contains a forbidden demonstration value");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList applications = document.getElementsByTagName("Application");
            int clients = 0;
            Set<String> regNumbers = new HashSet<>();
            for (int index = 0; index < applications.getLength(); index++) {
                Element application = (Element) applications.item(index);
                String objectType = directText(application, "ObjectType");
                if ("Client".equals(objectType)) clients++;
                requireUnique(regNumbers, directText(application, "RegNumber"));
            }
            if (clients != requests.size()) throw new IllegalArgumentException("Generated merchant count differs from source");
            int sourceTerminals = requests.stream().mapToInt(this::terminalCount).sum();
            int devices = document.getElementsByTagName("DeviceInfo").getLength();
            if (devices != sourceTerminals) throw new IllegalArgumentException("Generated terminal count differs from source");
            for (Way4DryRunRequest request : requests) {
                var merchant = request.merchant();
                requireContains(text, request.applicationRegNumber()); requireContains(text, merchant.registrationNumber());
                requireContains(text, merchant.legalName()); requireContains(text, merchant.tradingName());
                for (Way4DryRunRequest.Outlet outlet : request.outlets()) {
                    requireContains(text, outlet.code()); requireContains(text, outlet.name());
                    requireContains(text, outlet.address().line1());
                }
            }
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Cannot verify generated WAY4 XML", exception); }
    }

    private int terminalCount(Way4DryRunRequest request) {
        if (request.outlets() == null) return 0;
        return request.outlets().stream().filter(Objects::nonNull)
                .flatMap(outlet -> outlet.terminalRequests() == null ? java.util.stream.Stream.empty()
                        : outlet.terminalRequests().stream()).mapToInt(Way4DryRunRequest.TerminalRequest::quantity).sum();
    }
    private static void requireUnique(Set<String> values, String value) {
        if (value == null || value.isBlank() || !values.add(value))
            throw new IllegalArgumentException("Duplicate or empty WAY4 Application/RegNumber: " + value);
    }
    private static void requireContains(String xml, String value) {
        if (value != null && !value.isBlank() && !xml.contains(value))
            throw new IllegalArgumentException("Generated XML is inconsistent with source value: " + value);
    }
    private static String directText(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element && name.equals(element.getTagName())) return element.getTextContent();
        return null;
    }
}
