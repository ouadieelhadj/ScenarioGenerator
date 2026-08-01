package com.staging.sg.waypos.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("connected-e2e")
class WayPosSimulatorApplicationTest {
    @Test
    void startsWithoutDatabaseForConnectedE2e() {
    }
}
