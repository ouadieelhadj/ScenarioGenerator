package com.staging.sg.way4aura.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.way4aura.api.Way4DryRunRequest;
import com.staging.sg.way4aura.domain.AuraBinding;
import com.staging.sg.way4aura.domain.AuraBindingType;
import com.staging.sg.way4aura.repository.AuraBindingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "way4-aura.external-allocation.enabled=true",
        "way4-aura.external-allocation.environment=CARSDB",
        "way4-aura.generation-enabled=true",
        "way4-aura.submission-enabled=false",
        "way4-aura.batch.expected-merchants=1",
        "way4-aura.batch.expected-terminals=1",
        "way4-aura.xsd-root=D:/LanaCash/OpenWay/installationOCI/chargementxmlway4/schemas/xsd/xsd",
        "way4-aura.staging-directory=${way4.carsdb.proof.output.directory}"
})
@EnabledIfEnvironmentVariable(named="WAY4_CARSDB_INTEGRATION_TEST",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Way4ExternalIdentifierAllocatorPostgresqlTest {
    private static final UUID CASE_ID=UUID.fromString("198b8a1c-b686-4981-972a-ad7c7f383d04");
    private static final UUID OUTLET_ID=UUID.fromString("f8f61ca4-3bf0-3c43-9ec4-b941a6442693");
    private static final UUID TERMINAL_REQUEST_ID=UUID.fromString("14904900-9615-3bad-a19b-41a26d241348");

    @Autowired Way4ExternalIdentifierAllocator allocator;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuraBindingRepository bindings;
    @Autowired Way4DryRunService dryRun;
    @Autowired ObjectMapper json;

    @Test @Order(1) void replayKeepsTheSameThreeAllocationsWithoutConsumingNewSequenceValues() {
        assertEquals("carsdb",jdbc.queryForObject("select lower(current_database())",String.class));
        var first=allocator.allocate(CASE_ID,"ONB-198B8A1C",OUTLET_ID,TERMINAL_REQUEST_ID,1);
        List<Long> afterFirst=sequenceValues();
        var replay=allocator.allocate(CASE_ID,"ONB-198B8A1C",OUTLET_ID,TERMINAL_REQUEST_ID,1);
        List<Long> afterReplay=sequenceValues();

        assertEquals("990001000000001",first.mid());
        assertEquals("99000001",first.tid());
        assertEquals("LCAR00000001",first.merchantContractNumber());
        assertEquals(first,replay);
        assertEquals(afterFirst,afterReplay,"A replay must not consume another sequence value");
        Integer rows=jdbc.queryForObject("select count(*) from way4_external_identifier_allocation "
                + "where (allocation_type='MERCHANT_CONTRACT' and business_key='ONB-198B8A1C') "
                + "or (allocation_type='MID' and business_key=?) "
                + "or (allocation_type='TID' and business_key=?)",Integer.class,
                OUTLET_ID.toString(),TERMINAL_REQUEST_ID+":1");
        assertEquals(3,rows);
    }

    @Test @Order(2) void generatesTheMinimalCandidateThroughTheRealMappingAndAllocationPath() throws Exception {
        seedConfirmedBindings();
        Path source=Path.of("..","tests","merchant-onboarding","evidence","way4-carsdb-minimal",
                "carsdb-minimal-resident-merchant.json").normalize();
        Way4DryRunRequest request=json.readValue(source.toFile(),Way4DryRunRequest.class);
        var result=dryRun.generateBatch(List.of(request),"carsdb-minimal-controlled-import-1");
        assertTrue(result.fileName().matches("xadvapl000100_\\d{5}\\.\\d{3}"));
        assertTrue(result.xml().contains("<MerchantID>990001000000001</MerchantID>"));
        assertTrue(result.xml().contains("<ContractNumber>99000001</ContractNumber>"));
        assertTrue(result.xml().contains("<ContractNumber>LCAR00000001</ContractNumber>"));
        assertEquals("STAGED",result.status());
        var replay=dryRun.generateBatch(List.of(request),"carsdb-minimal-controlled-import-1");
        assertEquals(result.xmlSha256(),replay.xmlSha256());
        assertEquals(result.fileName(),replay.fileName());
    }

    @Test @Order(3) void recyclesOnlyTheRejectedSicWithoutNewIdentifiers() throws Exception {
        seedConfirmedBindings();
        bind(AuraBindingType.MCC,"5992","5992",Instant.parse("2026-01-01T00:00:00Z"));
        Path source=Path.of("..","tests","merchant-onboarding","evidence","way4-carsdb-minimal",
                "carsdb-minimal-resident-merchant-sic-5992.json").normalize();
        Way4DryRunRequest corrected=json.readValue(source.toFile(),Way4DryRunRequest.class);
        List<Long> sequencesBefore=sequenceValues();
        List<String> valuesBefore=allocatedValues();
        jdbc.update("update way4_application_state set status='WAY4_REJECTED_RETRYABLE' "
                + "where reg_number like 'ONB-198B8A1C%'");

        var recycled=dryRun.generateBatch(List.of(corrected),"carsdb-minimal-controlled-import-2");
        assertEquals("xadvapl000100_00002.225",recycled.fileName());
        assertTrue(recycled.xml().contains("<SIC>5992</SIC>"));
        assertFalse(recycled.xml().contains("<SIC>5411</SIC>"));
        assertTrue(recycled.xml().contains("<MerchantID>990001000000001</MerchantID>"));
        assertTrue(recycled.xml().contains("<ContractNumber>99000001</ContractNumber>"));
        assertTrue(recycled.xml().contains("<ContractNumber>LCAR00000001</ContractNumber>"));
        assertEquals(sequencesBefore,sequenceValues());
        assertEquals(valuesBefore,allocatedValues());
        var replay=dryRun.generateBatch(List.of(corrected),"carsdb-minimal-controlled-import-2");
        assertEquals(recycled.xmlSha256(),replay.xmlSha256());
        assertEquals(recycled.fileName(),replay.fileName());
    }

    @Test @Order(4) void generatesAcceptedCarsdbHierarchyWithoutNewIdentifiers() throws Exception {
        seedConfirmedBindings();
        bind(AuraBindingType.MCC,"5992","5992",Instant.parse("2026-01-01T00:00:00Z"));
        Path source=Path.of("..","tests","merchant-onboarding","evidence","way4-carsdb-minimal",
                "carsdb-minimal-resident-merchant-sic-5992.json").normalize();
        Way4DryRunRequest corrected=json.readValue(source.toFile(),Way4DryRunRequest.class);
        List<Long> sequencesBefore=sequenceValues();
        List<String> valuesBefore=allocatedValues();
        jdbc.update("update way4_application_state set status='WAY4_REJECTED_RETRYABLE' "
                + "where reg_number like 'ONB-198B8A1C%'");

        var adapted=dryRun.generateBatch(List.of(corrected),"carsdb-minimal-controlled-import-3");
        assertTrue(adapted.fileName().matches("xadvapl000100_\\d{5}\\.225"));
        assertTrue(adapted.fileNumber() > 2,"The adapted hierarchy must use a new file number");
        String xml=adapted.xml();
        int group=xml.indexOf("<ProductCode1>ARGROUP</ProductCode1>");
        int chain=xml.indexOf("<ProductCode1>ARCHAIN</ProductCode1>");
        int outlet=xml.indexOf("<ProductCode1>AROUTLET</ProductCode1>");
        int terminal=xml.indexOf("<ProductCode1>ARPOS</ProductCode1>");
        assertTrue(group >= 0 && group < chain && chain < outlet && outlet < terminal,
                "Expected hierarchy ARGROUP -> ARCHAIN -> AROUTLET -> ARPOS");
        assertFalse(xml.contains("<AccountScheme>"));
        assertFalse(xml.contains("<ServicePack>"));
        assertTrue(xml.contains("<SIC>5992</SIC>"));
        assertTrue(xml.contains("<MerchantID>990001000000001</MerchantID>"));
        assertTrue(xml.contains("<ContractNumber>99000001</ContractNumber>"));
        assertTrue(xml.contains("<ContractNumber>LCAR00000001</ContractNumber>"));
        assertEquals(sequencesBefore,sequenceValues());
        assertEquals(valuesBefore,allocatedValues());
        var replay=dryRun.generateBatch(List.of(corrected),"carsdb-minimal-controlled-import-3");
        assertEquals(adapted.xmlSha256(),replay.xmlSha256());
        assertEquals(adapted.fileName(),replay.fileName());
    }

    private void seedConfirmedBindings(){
        Instant at=Instant.parse("2026-01-01T00:00:00Z");
        bind(AuraBindingType.SENDER,"DEFAULT","000100",at);
        bind(AuraBindingType.INSTITUTION,"DEFAULT","0001",at);
        bind(AuraBindingType.ORDER_DEPARTMENT,"DEFAULT","0101",at);
        bind(AuraBindingType.CLIENT_TYPE,"RESIDENT","M_RES",at);
        bind(AuraBindingType.CLIENT_CATEGORY,"MERCHANT","Commercial",at);
        bind(AuraBindingType.GROUP_PRODUCT,"DEFAULT","ARGROUP",at);
        bind(AuraBindingType.CHAIN_PRODUCT,"DEFAULT","ARCHAIN",at);
        String product="5480f18c-14a4-4e87-8fe2-13782efc55c9";
        bind(AuraBindingType.ACCOUNT_PRODUCT,product,"AROUTLET",at);
        bind(AuraBindingType.ACCOUNT_SCHEME,product,"ARAS",at);
        bind(AuraBindingType.SERVICE_PACK,product,"CAA",at);
        bind(AuraBindingType.PAYMENT_ADDRESS_TYPE,"SETTLEMENT","OWS_PS",at);
        bind(AuraBindingType.POS_PRODUCT,product,"ARPOS",at);
        bind(AuraBindingType.DEVICE_ACCOUNT_SCHEME,product,"ARAS",at);
        bind(AuraBindingType.DEVICE_SERVICE_PACK,product,"ARPOS-R-MAIN",at);
        bind(AuraBindingType.DEVICE_TYPE,"FEITIAN","FEITIAN_OW_NATIVE",at);
        bind(AuraBindingType.DEVICE_TYPE,"S2M","S2M_OW_NATIVE",at);
        bind(AuraBindingType.COUNTRY,"MA","MAR",at);
        bind(AuraBindingType.CURRENCY,"504","MAD",at);
        bind(AuraBindingType.MCC,"5411","5411",at);
    }

    private void bind(AuraBindingType type,String source,String code,Instant validFrom){
        if(bindings.resolve(type,source,Instant.now()).isEmpty())
            bindings.save(AuraBinding.create(type,source,code,2,validFrom,null,
                    "Validator-confirmed CARSDB mapping 2026-08-13","carsdb-integration-proof"));
    }

    private List<Long> sequenceValues(){
        return List.of(
                jdbc.queryForObject("select last_value from way4_external_mid_seq",Long.class),
                jdbc.queryForObject("select last_value from way4_external_tid_seq",Long.class),
                jdbc.queryForObject("select last_value from way4_merchant_contract_number_seq",Long.class));
    }
    private List<String> allocatedValues(){
        return jdbc.queryForList("select allocation_type||'='||allocated_value from "
                + "way4_external_identifier_allocation order by allocation_type",String.class);
    }
}
