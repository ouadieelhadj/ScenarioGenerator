package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.ecommerce.simulator.service.EcommerceSimulatorClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MerchantSiteUiControllerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void returnsTheProvisionedProfileWithoutSensitiveCardData() throws Exception {
        UUID profileId = UUID.randomUUID();
        Path profile = temporaryDirectory.resolve("profile-id");
        Files.writeString(profile, profileId.toString());
        MerchantSiteUiController controller = new MerchantSiteUiController(
                mock(EcommerceSimulatorClient.class), profile.toString());

        var response = controller.configuration();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("profileId")).isEqualTo(profileId);
        assertThat(body.containsKey("pan")).isFalse();
        assertThat(body.containsKey("expiry")).isFalse();
        assertThat(body.containsKey("otp")).isFalse();
    }

    @Test
    void failsClosedWhenTheMerchantProfileIsMissing() {
        MerchantSiteUiController controller = new MerchantSiteUiController(
                mock(EcommerceSimulatorClient.class),
                temporaryDirectory.resolve("missing").toString());

        assertThat(controller.configuration().getStatusCode().value()).isEqualTo(503);
    }
}
