package com.staging.sg.softpos;

import static com.staging.sg.softpos.contracts.SoftPosContracts.*;
import static org.junit.jupiter.api.Assertions.*;
import com.staging.sg.common.iso.*;
import com.staging.sg.softpos.domain.SoftPosPosServerRoute;
import com.staging.sg.softpos.service.PersistentIsoPosServerConnector;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;
import org.jpos.core.SimpleConfiguration;
import org.jpos.iso.*;
import org.junit.jupiter.api.Test;

class PersistentIsoPosServerConnectorTest {
    @Test void exchangesTwoPaymentsOnPersistentIsoSession() throws Exception {
        int port; try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
        WayPosPackager packager = new WayPosPackager(); AtomicInteger messages = new AtomicInteger();
        ISOServer server = new ISOServer(port, new WayPosLengthChannel(packager), null);
        server.setConfiguration(new SimpleConfiguration());
        server.addISORequestListener((source, request) -> {
            try {
                messages.incrementAndGet(); ISOMsg response = (ISOMsg) request.clone(); response.setPackager(packager);
                response.setMTI("0210"); response.set(39, "00"); response.set(38, "ABC123"); source.send(response); return true;
            } catch (Exception e) { return false; }
        });
        Thread thread = new Thread(server, "softpos-iso-test"); thread.setDaemon(true); thread.start(); Thread.sleep(200);
        try {
            var connector = new PersistentIsoPosServerConnector(packager);
            var route = SoftPosPosServerRoute.configured("MEMBER-A", "LAB", PosServerMode.ISO8583_PERSISTENT,
                    "127.0.0.1:" + port, 1000, 3000, true);
            assertEquals(TransactionStatus.APPROVED, connector.exchange(RestJsonPosServerConnectorTest.command("ISO-TX-1"), route).status());
            assertEquals(TransactionStatus.APPROVED, connector.exchange(RestJsonPosServerConnectorTest.command("ISO-TX-2"), route).status());
            assertEquals(2, messages.get());
        } finally { server.shutdown(); thread.interrupt(); }
    }
}
