package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.api.Way4DryRunRequest;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Way4XmlGeneratorXsdTest {
    private static final String HASH="F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680";
    @Test void generatesPortalXmlWithoutWay4ContractNumbersAndMatchesXsd(){
        Path root=Path.of("D:/LanaCash/OpenWay/installationOCI/chargementxmlway4/schemas/xsd/xsd");
        Assumptions.assumeTrue(Files.isDirectory(root));
        UUID caseId=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID outletId=UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID requestId=UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID productId=UUID.fromString("55555555-5555-5555-5555-555555555555");
        var address=new Way4DryRunRequest.Address("Adresse autorisee de recette",null,null,"Casablanca",null,"20000","MAR");
        var terminal=new Way4DryRunRequest.TerminalRequest(requestId,productId,1,"POS_MODEL","4G",List.of());
        var outlet=new Way4DryRunRequest.Outlet(outletId,"OUT1","PDV",true,address,List.of(),List.of(terminal));
        var request=new Way4DryRunRequest("2.0",caseId,"PORTAL-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",productId,
                new Way4DryRunRequest.Merchant("PM","RCTEST0001","TAXTEST0001","Merchant Recette","Merchant",address,"5411"),
                new Way4DryRunRequest.Settlement("ACCOUNT-REF","504"),List.of(outlet),"merchant-way4-v2:"+caseId);
        var device=new ResolvedWay4Application.ResolvedDevice(outlet,terminal,1,
                Way4RegNumbers.device(request.applicationRegNumber(),requestId,1),"POS","POS","MAD","5411",1);
        var resolved=new ResolvedWay4Application("000100","0001","0101","MERCHANT","Commercial","ACQACC","ACQ","STANDARD",
                "PAYMENT","MAD","MAR","990000000000001",request,List.of(device),1,Instant.parse("2026-08-10T10:00:00Z"));
        var generator=new Way4XmlGenerator();Instant at=Instant.parse("2026-08-10T10:00:00Z");
        byte[] first=generator.generate(resolved,1,at);byte[] replay=generator.generate(resolved,1,at);assertArrayEquals(first,replay);
        String xml=new String(first,StandardCharsets.UTF_8);assertFalse(xml.contains("<ContractNumber>"));
        assertTrue(xml.contains("<MerchantID>990000000000001</MerchantID>"));assertFalse(xml.contains("LOCAL_"));
        var validation=new Way4XsdValidator(root.toString(),"offline/WAY4ApplFile.xsd",HASH).validate(first);
        assertTrue(validation.valid());assertEquals(HASH,validation.xsdSha256());
    }
    @Test void blocksWhenXsdRootIsNotConfigured(){var validator=new Way4XsdValidator("","offline/WAY4ApplFile.xsd",HASH);
        assertThrows(AuraMappingBlockedException.class,()->validator.validate("<ApplicationFile/>".getBytes(StandardCharsets.UTF_8)));}
}
