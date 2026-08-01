package com.staging.sg.ecommerce.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "server.port=0")
class EcommerceSimulatorApplicationTest {
    @Test
    void contextStartsWithoutDatasource() {}
}
