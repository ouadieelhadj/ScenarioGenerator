package com.staging.sg.fraud.gateway.network;

import org.jpos.iso.*;
import org.jpos.iso.channel.NACChannel;
import org.jpos.iso.packager.ISO87APackager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "fraud-gateway.lab.multibank-bootstrap-enabled=true",
        "fraud-gateway.routing.dynamic-listeners-enabled=true",
        "fraud-gateway.routing.enforce-dedicated-rest-ports=false",
        "fraud-gateway.iso.permanent-enabled=false",
        "fraud-gateway.iso.client-enabled=false"
})
class MultibankListenerIntegrationTest {
    @Test
    void startsOneIsoAndOneRestPortForEachBank() throws Exception {
        for (int port : new int[]{8601, 8602, 8603}) assertSignOn(port);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        for (int port : new int[]{8701, 8702, 8703}) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/fraud-gateway/v1/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("GATEWAY_REQUIRED");
        }
    }

    private void assertSignOn(int port) throws Exception {
        NACChannel channel = new NACChannel("127.0.0.1", port, new ISO87APackager(), null);
        channel.connect();
        try {
            ISOMsg request = new ISOMsg(); request.setPackager(new ISO87APackager());
            request.setMTI("0800"); request.set(7, "0817102000"); request.set(11, "000001"); request.set(70, "001");
            channel.send(request); ISOMsg response = channel.receive();
            assertThat(response.getMTI()).isEqualTo("0810"); assertThat(response.getString(39)).isEqualTo("00");
        } finally { channel.disconnect(); }
    }
}
