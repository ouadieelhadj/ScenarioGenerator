package com.staging.sg.way4aura.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.way4aura.api.Way4DryRunRequest;
import com.staging.sg.way4aura.domain.Way4FileBatch;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Way4XmlGeneratorXsdTest {
    private static final String HASH="F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680";
    private static final Path XSD_ROOT=Path.of("D:/LanaCash/OpenWay/installationOCI/chargementxmlway4/schemas/xsd/xsd");
    private static final Path EVIDENCE=Path.of("..","tests","merchant-onboarding","evidence","three-channels").normalize();
    private static final Path CARSDB_EVIDENCE=Path.of("..","tests","merchant-onboarding","evidence","way4-carsdb-minimal").normalize();
    private static final ObjectMapper JSON=new ObjectMapper();

    @Test void generatesPortalXmlWithoutWay4AllocatedIdentifiersAndMatchesXsd(){
        Assumptions.assumeTrue(Files.isDirectory(XSD_ROOT));
        UUID caseId=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID outletId=UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID requestId=UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID productId=UUID.fromString("55555555-5555-5555-5555-555555555555");
        var address=new Way4DryRunRequest.Address("Casablanca",null,null,"Casablanca",null,null,"MA");
        var terminal=new Way4DryRunRequest.TerminalRequest(requestId,productId,1,"PORTAL_POS",null,List.of());
        var outlet=new Way4DryRunRequest.Outlet(outletId,"OUT-AAAA","Point de vente",true,address,List.of(),List.of(terminal));
        var request=new Way4DryRunRequest("2.0",caseId,"ONB-AAAAAAAA",productId,
                new Way4DryRunRequest.Merchant("PORTAL_MERCHANT","RCAAAA",null,"Commerce A","Boutique A",address,"5411"),
                new Way4DryRunRequest.Settlement("ACCOUNT-REF","504"),List.of(outlet),"merchant-way4-v2:"+caseId);
        var device=new ResolvedWay4Application.ResolvedDevice(outlet,terminal,1,
                Way4RegNumbers.device(request.applicationRegNumber(),1),"ARPOS","ARAS","ARPOS-R-MAIN",
                "FEITIAN_OW_NATIVE","MAD","5411","990001000000001","99000001",1);
        var resolved=new ResolvedWay4Application("000100","0001","0101","M_RES","Commercial","ARGROUP","ARCHAIN","AROUTLET","ARAS","CAA",
                "OWS_PS","MAD","MAR","LCAR00000001",request,List.of(device),1,Instant.parse("2026-08-13T10:00:00Z"));
        byte[] xml=new Way4XmlGenerator().generate(resolved,1,Instant.parse("2026-08-13T10:00:00Z"));
        String text=new String(xml,StandardCharsets.UTF_8);
        assertTrue(text.contains("<MerchantID>990001000000001</MerchantID>"));
        assertTrue(text.contains("<ContractNumber>LCAR00000001</ContractNumber>"));
        assertTrue(text.contains("<ContractNumber>99000001</ContractNumber>"));
        assertTrue(new Way4XsdValidator(XSD_ROOT.toString(),"offline/WAY4ApplFile.xsd",HASH).validate(xml).valid());
    }

    @Test void blocksWhenXsdRootIsNotConfigured(){
        var validator=new Way4XsdValidator("","offline/WAY4ApplFile.xsd",HASH);
        assertThrows(AuraMappingBlockedException.class,()->validator.validate("<ApplicationFile/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void generatesMinimalCarsdbCandidateWithApprovedExternalIdentifiers() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(XSD_ROOT));
        Way4DryRunRequest request=JSON.readValue(CARSDB_EVIDENCE.resolve("carsdb-minimal-resident-merchant.json").toFile(),Way4DryRunRequest.class);
        var outlet=request.outlets().get(0);var terminal=outlet.terminalRequests().get(0);
        var device=new ResolvedWay4Application.ResolvedDevice(outlet,terminal,1,
                Way4RegNumbers.device(request.applicationRegNumber(),1),"ARPOS","ARAS","ARPOS-R-MAIN",
                "FEITIAN_OW_NATIVE","MAD","5411","990001000000001","99000001",2);
        var resolved=new ResolvedWay4Application("000100","0001","0101","M_RES","Commercial",
                "ARGROUP","ARCHAIN","AROUTLET","ARAS","CAA","OWS_PS","MAD","MAR","LCAR00000001",
                request,List.of(device),2,Instant.parse("2026-08-13T14:30:00Z"));
        Way4FileBatch batch=Way4FileBatch.draft(9,"000100","carsdb-minimal-proof","proof",2,
                Instant.parse("2026-08-13T14:30:00Z"));
        byte[] xml=new Way4XmlGenerator().generate(resolved,batch.fileNumber(),batch.generatedAt());
        new Way4BatchIntegrityValidator(1,1).validateGeneratedXml(xml,List.of(request));
        String text=new String(xml,StandardCharsets.UTF_8);
        assertEquals(1,count(text,"<ObjectType>Client</ObjectType>"));
        assertEquals(4,count(text,"<ObjectType>Contract</ObjectType>"));
        assertEquals(1,count(text,"<DeviceInfo>"));
        assertTrue(text.contains("<ProductCode1>AROUTLET</ProductCode1>"));
        assertTrue(text.contains("<ProductCode1>ARPOS</ProductCode1>"));
        assertTrue(text.contains("<ProductCode1>ARGROUP</ProductCode1>"));
        assertTrue(text.contains("<ProductCode1>ARCHAIN</ProductCode1>"));
        assertFalse(text.contains("<AccountScheme>"));
        assertFalse(text.contains("<ServicePack>"));
        assertTrue(text.contains("<DeviceType>FEITIAN_OW_NATIVE</DeviceType>"));
        assertTrue(text.contains("<MerchantID>990001000000001</MerchantID>"));
        assertTrue(text.contains("<ContractNumber>LCAR00000001</ContractNumber>"));
        assertTrue(text.contains("<ContractNumber>99000001</ContractNumber>"));
        assertEquals(2,count(text,"<ContractIDT>"));
        assertTrue(new Way4XsdValidator(XSD_ROOT.toString(),"offline/WAY4ApplFile.xsd",HASH).validate(xml).valid());
        String outputDirectory=System.getProperty("way4.carsdb.proof.output.directory");
        if(outputDirectory!=null&&!outputDirectory.isBlank()){
            Path output=Path.of(outputDirectory).toAbsolutePath().normalize();Files.createDirectories(output);
            Files.write(output.resolve(batch.extendedFileName()),xml);
        }
    }

    @Test void generatesThreeRealPortalJourneysInOneValidatedFile() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(XSD_ROOT));
        ProofBindings bindings=JSON.readValue(EVIDENCE.resolve("way4-bindings-pending-aura-validation.json").toFile(),ProofBindings.class);
        assertFalse(bindings.validatedForImport,"Proof bindings must remain explicitly non-importable");
        List<ResolvedWay4Application> resolved=List.of(
                resolvedFromJson("merchant-web-canonical-acquiring.json",bindings),
                resolvedFromJson("commercial-web-canonical-acquiring.json",bindings),
                resolvedFromJson("mobile-canonical-acquiring.json",bindings));
        List<Way4DryRunRequest> requests=resolved.stream().map(ResolvedWay4Application::request).toList();
        var integrity=new Way4BatchIntegrityValidator(3,6);
        integrity.validateSources(requests);
        Way4FileBatch batch=Way4FileBatch.draft(8,bindings.sender,"proof-three-portal-journeys","proof",
                bindings.mappingVersion,Instant.parse("2026-08-13T10:00:00Z"));
        byte[] xml=new Way4XmlGenerator().generate(resolved,batch.fileNumber(),batch.generatedAt());
        integrity.validateGeneratedXml(xml,requests);
        String text=new String(xml,StandardCharsets.UTF_8);
        String julian=DateTimeFormatter.ofPattern("DDD").withZone(ZoneOffset.UTC).format(batch.generatedAt());
        String creationDate=DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(batch.generatedAt());
        assertEquals("xadvapl"+bindings.sender+"_00008."+julian,batch.extendedFileName());
        assertTrue(text.contains("<Sender>"+bindings.sender+"</Sender>"));
        assertTrue(text.contains("<CreationDate>"+creationDate+"</CreationDate>"));
        assertEquals(3,count(text,"<ObjectType>Client</ObjectType>"));
        assertEquals(15,count(text,"<ObjectType>Contract</ObjectType>"));
        assertEquals(6,count(text,"<DeviceInfo>"));
        assertEquals(6,count(text,"<MerchantID>")); assertEquals(9,count(text,"<ContractIDT>"));
        assertFalse(text.matches("(?is).*(FIRST|SECOND|\\bTEST\\b).*"));
        assertTrue(text.contains("<Country>MAR</Country>")); assertTrue(text.contains("<DefaultCurr>MAD</DefaultCurr>"));
        assertEquals(6,count(text,"<SIC>5411</SIC>"));
        assertTrue(text.contains("<Number>00008</Number>"));
        assertTrue(new Way4XsdValidator(XSD_ROOT.toString(),"offline/WAY4ApplFile.xsd",HASH).validate(xml).valid());
        String outputDirectory=System.getProperty("way4.proof.output.directory");
        if(outputDirectory!=null&&!outputDirectory.isBlank()){
            Path output=Path.of(outputDirectory).toAbsolutePath().normalize();Files.createDirectories(output);
            Files.write(output.resolve(batch.extendedFileName()),xml);
        }
    }

    private static ResolvedWay4Application resolvedFromJson(String fileName,ProofBindings bindings)throws Exception{
        JsonNode source=JSON.readTree(EVIDENCE.resolve(fileName).toFile());
        UUID caseId=UUID.fromString(source.path("onboardingCaseId").asText());
        String root=source.path("onboardingReference").asText();
        UUID productId=UUID.fromString(source.path("productId").asText());
        JsonNode outletSource=source.path("outlet");String outletAddress=outletSource.path("address").asText();
        var address=new Way4DryRunRequest.Address(outletAddress,null,null,outletAddress,null,null,source.path("country").asText());
        UUID outletId=UUID.nameUUIDFromBytes((caseId+":"+outletSource.path("code").asText()).getBytes(StandardCharsets.UTF_8));
        UUID terminalRequestId=UUID.nameUUIDFromBytes((caseId+":TERMINALS").getBytes(StandardCharsets.UTF_8));
        int terminalCount=outletSource.path("terminalCount").asInt();
        var terminal=new Way4DryRunRequest.TerminalRequest(terminalRequestId,productId,terminalCount,
                "PORTAL_POS",null,List.of());
        var outlet=new Way4DryRunRequest.Outlet(outletId,outletSource.path("code").asText(),outletSource.path("name").asText(),
                true,address,List.of(),List.of(terminal));
        var request=new Way4DryRunRequest("2.0",caseId,root,productId,
                new Way4DryRunRequest.Merchant("PORTAL_MERCHANT",source.path("registrationNumber").asText(),null,
                        source.path("legalName").asText(),source.path("tradingName").asText(),address,source.path("mcc").asText()),
                new Way4DryRunRequest.Settlement(source.path("settlementAccountReference").asText(),
                        source.path("settlementCurrency").asText()),List.of(outlet),"merchant-way4-v2:"+caseId);
        List<ResolvedWay4Application.ResolvedDevice> devices=new ArrayList<>();
        for(int ordinal=1;ordinal<=terminalCount;ordinal++)devices.add(new ResolvedWay4Application.ResolvedDevice(
                outlet,terminal,ordinal,Way4RegNumbers.device(root,ordinal),bindings.posProduct,bindings.posAccountScheme,
                bindings.posServicePack,bindings.deviceType,bindings.currency,bindings.mcc,
                "99000100000000"+ordinal,String.format("99%06d",ordinal),bindings.mappingVersion));
        return new ResolvedWay4Application(bindings.sender,bindings.institution,bindings.orderDepartment,
                bindings.clientType,bindings.clientCategory,bindings.groupProduct,bindings.chainProduct,
                bindings.accountProduct,bindings.accountScheme,
                bindings.servicePack,bindings.paymentAddressType,bindings.currency,bindings.country,"LCAR00000001",
                request,List.copyOf(devices),bindings.mappingVersion,Instant.parse("2026-08-13T10:00:00Z"));
    }

    public static class ProofBindings {
        public boolean validatedForImport; public int mappingVersion; public String sender; public String institution;
        public String orderDepartment; public String clientType; public String clientCategory; public String accountProduct;
        public String groupProduct; public String chainProduct;
        public String accountScheme; public String servicePack; public String paymentAddressType; public String posProduct;
        public String posAccountScheme; public String posServicePack; public String deviceType;
        public String country; public String currency; public String mcc;
    }
    private static int count(String value,String pattern){int count=0,index=0;while((index=value.indexOf(pattern,index))>=0){count++;index+=pattern.length();}return count;}
}
