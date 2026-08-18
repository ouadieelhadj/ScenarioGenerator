package com.staging.sg.softpos.service;

import com.staging.sg.common.iso.*;
import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import com.staging.sg.softpos.domain.SoftPosPosServerRoute;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class PersistentIsoPosServerConnector implements PosServerConnector {
    private final WayPosPackager packager;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger stans = new AtomicInteger();
    public PersistentIsoPosServerConnector(WayPosPackager packager) { this.packager = packager; }
    @Override public PosServerMode mode() { return PosServerMode.ISO8583_PERSISTENT; }

    @Override public PosServerPaymentResult exchange(PosServerPaymentCommand command, SoftPosPosServerRoute route) throws Exception {
        Session session = sessions.computeIfAbsent(route.getMemberId() + '|' + route.getEnvironment(), ignored -> new Session());
        session.lock.lock();
        try {
            WayPosLengthChannel channel = connected(session, route);
            ISOMsg request = message(command); channel.send(request); ISOMsg response = channel.receive();
            if (!request.getString(11).equals(response.getString(11))) throw new IllegalStateException("ISO correlation mismatch");
            String rc = response.getString(39);
            return new PosServerPaymentResult(("00".equals(rc) || "10".equals(rc)) ? TransactionStatus.APPROVED : TransactionStatus.DECLINED,
                    rc, response.hasField(38) ? response.getString(38) : null);
        } catch (Exception e) {
            close(session); throw e;
        } finally { session.lock.unlock(); }
    }

    private WayPosLengthChannel connected(Session session, SoftPosPosServerRoute route) throws Exception {
        if (session.channel != null && session.channel.isConnected()) return session.channel;
        String[] endpoint = route.getEndpoint().split(":", 2);
        if (endpoint.length != 2) throw new IllegalArgumentException("ISO endpoint must be host:port");
        WayPosLengthChannel channel = new WayPosLengthChannel(endpoint[0], Integer.parseInt(endpoint[1]), packager);
        channel.setTimeout(route.getResponseTimeoutMillis()); channel.connect();
        session.channel = channel; return channel;
    }

    private ISOMsg message(PosServerPaymentCommand command) throws Exception {
        if (!"LABREF:APPROVED_CARD".equals(command.sdkCredentialReference()))
            throw new IllegalStateException("Certified SDK/HSM ISO credential adapter is not configured");
        String laboratoryPan = "4000000000000002";
        String laboratoryExpiry = "2912";
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC); String stan = "%06d".formatted(stans.updateAndGet(v -> v >= 999999 ? 1 : v + 1));
        ISOMsg m = new ISOMsg(); m.setPackager(packager); m.setMTI("0200"); m.set(2, laboratoryPan); m.set(3, "000000");
        m.set(4, "%012d".formatted(command.amountMinor())); m.set(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        m.set(11, stan); m.set(12, now.format(DateTimeFormatter.ofPattern("HHmmss"))); m.set(13, now.format(DateTimeFormatter.ofPattern("MMdd")));
        m.set(14, laboratoryExpiry); m.set(22, command.acceptanceChannel() == AcceptanceChannel.NFC ? "071" : "010"); m.set(25, "00");
        m.set(41, fixed(command.terminalId(), 8)); m.set(42, fixed(command.merchantId(), 15)); m.set(43, fixed("FUTURPAYMENT SOFTPOS LAB MA", 40));
        m.set(49, command.currency()); m.set(63, "007SV1.0.0"); return m;
    }
    private static String fixed(String value, int length) { return value.length() >= length ? value.substring(0, length) : value + " ".repeat(length - value.length()); }
    private static void close(Session s) { try { if (s.channel != null && s.channel.isConnected()) s.channel.disconnect(); } catch (Exception ignored) {} s.channel = null; }
    private static final class Session { private final ReentrantLock lock = new ReentrantLock(); private WayPosLengthChannel channel; }
}
