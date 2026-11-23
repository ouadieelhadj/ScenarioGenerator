package com.moneycore.merchantportal;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class IncidentDownloadPolicyTest {
    private final String id = "11111111-2222-4333-8444-555555555555";
    @Test public void threeExplicitMimeTypesAndMatchingOpaqueNames() {
        assertTrue(IncidentDownloadPolicy.validName("incident-" + id + ".pdf", "application/pdf"));
        assertTrue(IncidentDownloadPolicy.validName("incident-" + id + ".png", "image/png"));
        assertTrue(IncidentDownloadPolicy.validName("incident-" + id + ".jpg", "image/jpeg"));
        for (String bad : new String[]{"../private", "incident-" + id + ".html", "incident-" + id + ".pdf/extra", "incident-not-a-uuid.pdf", null}) assertFalse(IncidentDownloadPolicy.validName(bad, "application/pdf"));
        assertFalse(IncidentDownloadPolicy.validName("incident-" + id + ".pdf", "text/html"));
        assertFalse(IncidentDownloadPolicy.validName("incident-" + id + ".pdf", "image/png"));
    }
    @Test public void strictBase64Envelope() {
        assertTrue(IncidentDownloadPolicy.validBase64("JVBERi0="));
        for (String bad : new String[]{null, "", "A", "AAAAA", "AA=A", "A===", "AAAA\n", "data:application/pdf;base64,JVBERi0=", "-___"}) assertFalse(IncidentDownloadPolicy.validBase64(bad));
    }
    @Test public void decodedSizeIsBoundedBeforeAllocation() {
        assertTrue(IncidentDownloadPolicy.validBase64("A".repeat(IncidentDownloadPolicy.MAX_BASE64 - 2) + "=="));
        assertFalse(IncidentDownloadPolicy.validBase64("A".repeat(IncidentDownloadPolicy.MAX_BASE64)));
        assertFalse(IncidentDownloadPolicy.validBase64("A".repeat(IncidentDownloadPolicy.MAX_BASE64 + 4)));
    }
    @Test public void headerAndMimeMustAgreeWithoutParsingTheDocument() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        byte[] png = {(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        byte[] jpeg = {(byte)0xff, (byte)0xd8, (byte)0xff};
        assertTrue(IncidentDownloadPolicy.validContent("application/pdf", pdf));
        assertTrue(IncidentDownloadPolicy.validContent("image/png", png));
        assertTrue(IncidentDownloadPolicy.validContent("image/jpeg", jpeg));
        assertFalse(IncidentDownloadPolicy.validContent("image/png", pdf));
        assertFalse(IncidentDownloadPolicy.validContent("text/html", pdf));
        assertFalse(IncidentDownloadPolicy.validContent("application/pdf", new byte[0]));
        assertFalse(IncidentDownloadPolicy.validContent("application/pdf", null));
    }
}
