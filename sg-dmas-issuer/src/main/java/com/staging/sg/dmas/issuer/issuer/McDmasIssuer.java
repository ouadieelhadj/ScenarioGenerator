package com.staging.sg.dmas.issuer.issuer;

import com.staging.sg.common.iso.DmasNetworkUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Issuer DMAS — Socket server EBCDIC.
 * Même pattern que McIssuer (ASCII) mais via DmasNetworkUtil (McPackagerEbcdic).
 *
 * Étape sign-on : traite 0800 (DE070 001 sign-on / 002 sign-off / 270 echo)
 * et répond 0810 DE39=00. PAS de crypto à ce stade (sign-on précède
 * l'échange PEK/MAK dans la séquence DMAS).
 */
@Service
public class McDmasIssuer {

    private static final Logger log = LoggerFactory.getLogger(McDmasIssuer.class);

    private final DmasNetworkUtil net;

    @Value("${dmas.iso-port:8500}")
    private int isoPort;

    private Thread       serverThread;
    private ServerSocket serverSocket;
    private final AtomicLong msgCount = new AtomicLong(0);

    public McDmasIssuer(DmasNetworkUtil net) {
        this.net = net;
    }

    @PostConstruct
    public void start() {
        serverThread = new Thread(this::runServer, "dmas-issuer-server");
        serverThread.setDaemon(true);
        serverThread.start();
        log.info("[DMAS-ISS] Server starting — port {}", isoPort);
    }

    @PreDestroy
    public void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (serverThread != null) serverThread.interrupt();
        log.info("[DMAS-ISS] Server stopped");
    }

    public long getMessageCount() { return msgCount.get(); }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(isoPort);
            log.info("[DMAS-ISS] Listening on :{} (EBCDIC)", isoPort);
            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();
                long id = msgCount.incrementAndGet();
                Thread t = new Thread(() -> handleConnection(client), "dmas-issuer-client-" + id);
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted())
                log.error("[DMAS-ISS] Server error : {}", e.getMessage());
        }
    }

    private void handleConnection(Socket socket) {
        try {
            DataInputStream  in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ISOMsg request = net.receive(in);
            String mti = request.getMTI();
            switch (mti) {
                case "0800" -> handleNetworkMessage(request, out);
                default     -> log.warn("[DMAS-ISS] MTI non géré (sign-on step) : {}", mti);
            }
        } catch (Exception e) {
            log.error("[DMAS-ISS] Connection error : {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleNetworkMessage(ISOMsg request, DataOutputStream out) throws Exception {
        String de70 = net.safeGet(request, 70);
        String stan = net.safeGet(request, 11);
        String label = switch (de70 != null ? de70 : "") {
            case "001" -> "SIGN-ON";
            case "002" -> "SIGN-OFF";
            case "270" -> "ECHO";
            default    -> "NETWORK(" + de70 + ")";
        };
        log.info("[DMAS-ISS] Reçu 0800 {} — STAN={}", label, stan);

        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0810");
        if (request.hasField(7))  response.set(7,  request.getString(7));
        if (request.hasField(11)) response.set(11, request.getString(11));
        if (request.hasField(70)) response.set(70, request.getString(70));
        response.set(39, "00");

        net.send(out, response);
        log.info("[DMAS-ISS] {} -> réponse 0810 DE39=00", label);
    }
}
