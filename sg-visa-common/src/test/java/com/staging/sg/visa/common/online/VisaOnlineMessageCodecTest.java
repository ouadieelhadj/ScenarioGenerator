package com.staging.sg.visa.common.online;

import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisaOnlineMessageCodecTest {
    @Test
    void packsAndUnpacksEcommerceAuthorizationWithBinaryBitmap() throws Exception {
        ISOMsg request = new ISOMsg("0100");
        request.set(2, "4111111111111111");
        request.set(3, "000000");
        request.set(4, "000000001000");
        request.set(7, "0802123456");
        request.set(11, "000123");
        request.set(12, "123456");
        request.set(13, "0802");
        request.set(14, "2912");
        request.set(18, "5999");
        request.set(19, "504");
        request.set(22, "010");
        request.set(25, "59");
        request.set(32, "123456");
        request.set(37, "621512000123");
        request.set(41, "ECOM0001");
        request.set(42, "MID000000000001");
        request.set(43, "MERCHANT TEST           CASABLANCA MA");
        request.set(49, "504");
        request.set(60, "ECI=05");

        VisaOnlineMessageCodec codec = new VisaOnlineMessageCodec();
        ISOMsg decoded = codec.unpack(codec.pack(request));

        assertThat(decoded.getMTI()).isEqualTo("0100");
        assertThat(decoded.getString(2)).isEqualTo("4111111111111111");
        assertThat(decoded.getString(37)).isEqualTo("621512000123");
        assertThat(decoded.getString(60)).isEqualTo("ECI=05");
    }

    @Test
    void roundTripsVisaReferences() {
        String encoded = VisaField62Codec.encode("Y", "123456789012345", "A1B2");
        VisaOnlineReferences references = VisaField62Codec.decode(encoded);
        assertThat(references.aci()).isEqualTo("Y");
        assertThat(references.transactionId()).isEqualTo("123456789012345");
        assertThat(references.validationCode()).isEqualTo("A1B2");
        assertThat(references.provenance()).isEqualTo("SIMULATED_NETWORK");
    }
}
