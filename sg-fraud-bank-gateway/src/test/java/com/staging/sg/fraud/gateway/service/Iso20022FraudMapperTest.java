package com.staging.sg.fraud.gateway.service;

import com.staging.sg.fraud.gateway.api.OmnichannelApi.Iso20022EvaluationRequest;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class Iso20022FraudMapperTest {
    private final Iso20022FraudMapper mapper=new Iso20022FraudMapper();
    @Test void mapsNamespacedTransferWithoutExternalEntities(){
        String xml="""
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08"><FIToFICstmrCdtTrf><CdtTrfTxInf>
            <PmtId><EndToEndId>E2E-2026-001</EndToEndId></PmtId><IntrBkSttlmAmt Ccy="MAD">185.25</IntrBkSttlmAmt>
            <Cdtr><Nm>BENEFICIARY_TOKEN</Nm></Cdtr><CdtrAcct><Id><IBAN>ACCOUNT_TOKEN</IBAN></Id></CdtrAcct>
            </CdtTrfTxInf></FIToFICstmrCdtTrf></Document>
            """;
        var result=mapper.toUniversal(new Iso20022EvaluationRequest("INSTRUMENT_TOKEN",xml,"DEVICE_TOKEN","IP_TOKEN",Map.of("NEW_DEVICE",true)));
        assertThat(result.transactionId()).isEqualTo("E2E-2026-001");assertThat(result.amountMinor()).isEqualTo(18525);
        assertThat(result.currency()).isEqualTo("MAD");assertThat(result.domain()).isEqualTo("TRANSFER");assertThat(result.sourceProtocol()).isEqualTo("ISO20022");
        assertThat(result.signals()).containsEntry("NEW_DEVICE",true);
    }
    @Test void rejectsDoctype(){
        String xml="<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///c:/windows/win.ini'>]><Document><MsgId>&ext;</MsgId><Amt>1</Amt></Document>";
        assertThatThrownBy(()->mapper.toUniversal(new Iso20022EvaluationRequest("TOKEN",xml,null,null,Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsafe");
    }
}
