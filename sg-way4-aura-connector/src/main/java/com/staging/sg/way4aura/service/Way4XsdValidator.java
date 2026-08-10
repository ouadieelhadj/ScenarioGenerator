package com.staging.sg.way4aura.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class Way4XsdValidator {
    private final String xsdRoot;
    private final String mainXsd;
    private final String expectedHash;
    public Way4XsdValidator(@Value("${way4-aura.xsd-root:}") String xsdRoot,
            @Value("${way4-aura.main-xsd:offline/WAY4ApplFile.xsd}") String mainXsd,
            @Value("${way4-aura.expected-main-xsd-sha256:}") String expectedHash) {
        this.xsdRoot = xsdRoot; this.mainXsd = mainXsd; this.expectedHash = expectedHash;
    }
    public ValidationResult validate(byte[] xml) {
        try {
            if (xsdRoot.isBlank()) throw new AuraMappingBlockedException("WAY4 XSD root is not configured");
            Path root = Path.of(xsdRoot).toAbsolutePath().normalize();
            Path schemaPath = root.resolve(mainXsd).normalize();
            if (!schemaPath.startsWith(root) || !Files.isRegularFile(schemaPath))
                throw new AuraMappingBlockedException("WAY4 main XSD is missing");
            String actualHash = HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(schemaPath)));
            if (!actualHash.equalsIgnoreCase(expectedHash))
                throw new AuraMappingBlockedException("WAY4 main XSD fingerprint differs from the approved value");
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            Schema schema = factory.newSchema(schemaPath.toFile());
            schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));
            return new ValidationResult(true, actualHash);
        } catch (AuraMappingBlockedException exception) {
            throw exception;
        } catch (SAXException exception) {
            throw new Way4XsdRejectedException("Generated XML is rejected by the approved WAY4 XSD", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot validate WAY4 XML", exception);
        }
    }
    public record ValidationResult(boolean valid, String xsdSha256) {}
}
