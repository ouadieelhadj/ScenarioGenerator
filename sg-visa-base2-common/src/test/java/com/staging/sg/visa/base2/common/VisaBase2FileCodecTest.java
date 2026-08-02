package com.staging.sg.visa.base2.common;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VisaBase2FileCodecTest {
    @Test
    void buildsValidFiveRecordPurchaseCtfAndRoundTripsEbcdic() {
        VisaBase2PresentmentData data = new VisaBase2PresentmentData(
                "TX-1", "4111111111111111", VisaBase2Arn.generate("123456", LocalDate.of(2026, 8, 2), 1),
                "12345678", "0802", 1000, "504", 1000, "504", "MERCHANT TEST",
                "CASABLANCA", "MAR", "5999", "20000", "CAS", "Y", "ABC123", "10",
                "123456789012345", 1000, "504", "00", "A1B2");
        VisaBase2FileFactory factory = new VisaBase2FileFactory();
        VisaBase2FileCodec codec = new VisaBase2FileCodec();
        byte[] file = codec.pack(factory.purchaseCtf(data, "123456", LocalDate.of(2026, 8, 2), 1, 1));

        assertThat(file).hasSize(5 * VisaBase2Record.LENGTH);
        assertThat(codec.unpack(file)).extracting(VisaBase2Record::transactionCode)
                .containsExactly("90", "05", "05", "91", "92");
        assertThat(new VisaBase2FileValidator().validate(file).valid()).isTrue();
    }

    @Test
    void rejectsTruncatedCtf() {
        assertThat(new VisaBase2FileValidator().validate(new byte[167]).valid()).isFalse();
    }
}
