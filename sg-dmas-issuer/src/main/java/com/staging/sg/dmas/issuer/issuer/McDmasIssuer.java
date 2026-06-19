package com.staging.sg.dmas.issuer.issuer;

import com.staging.sg.common.entity.DmasKek;
import com.staging.sg.common.entity.KeyStore;
import com.staging.sg.common.iso.DmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.DmasKekRepository;
import com.staging.sg.common.repository.KeyStoreRepository;
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
 * Traite 0800 :
 *   - sign-on  (DE070 001/002/270)         -> 0810 DE39=00
 *   - key exch (DE070 101 PEK / 102 MAK)   -> importe sous kek_under_iss_lmk,
 *                                              vérifie KCV, stocke key_store,
 *                                              0810 DE39=00 (ou 30 si KCV KO)
 */
@Service
public class McDmasIssuer {

    private static final Logger log = LoggerFactory.getLogger(McDmasIssuer.class);

    private final DmasNetworkUtil net;
    private final HsmService hsm;
    private final DmasKekRepository kekRepo;
    private final KeyStoreRepository keyStoreRepo;

    @Value("${dmas.iso-port:8500}")
    private int isoPort;

    @Value("${dmas.member-group:TESTGRP01}")
    private String memberGroup;

    private Thread       serverThread;
    private ServerSocket serverSocket;
    private final AtomicLong msgCount = new AtomicLong(0);

    public McDmasIssuer(DmasNetworkUtil net, HsmService hsm,
                        DmasKekRepository kekRepo, KeyStoreRepository keyStoreRepo) {
        this.net = net;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.keyStoreRepo = keyStoreRepo;
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
                default     -> log.warn("[DMAS-ISS] MTI non géré : {}", mti);
            }
        } catch (Exception e) {
            log.error("[DMAS-ISS] Connection error : {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleNetworkMessage(ISOMsg request, DataOutputStream out) throws Exception {
        String de70 = net.safeGet(request, 70);
        if ("101".equals(de70) || "102".equals(de70)) {
            handleKeyExchange(request, out, de70);
        } else {
            handleSignOn(request, out, de70);
        }
    }

    private void handleSignOn(ISOMsg request, DataOutputStream out, String de70) throws Exception {
        String stan = net.safeGet(request, 11);
        String label = switch (de70 != null ? de70 : "") {
            case "001" -> "SIGN-ON";
            case "002" -> "SIGN-OFF";
            case "270" -> "ECHO";
            default    -> "NETWORK(" + de70 + ")";
        };
        log.info("[DMAS-ISS] Reçu 0800 {} — STAN={}", label, stan);

        ISOMsg response = buildResponse(request, "00");
        net.send(out, response);
        log.info("[DMAS-ISS] {} -> réponse 0810 DE39=00", label);
    }

    private void handleKeyExchange(ISOMsg request, DataOutputStream out, String de70) throws Exception {
        String keyType = "101".equals(de70) ? "PEK" : "MAK";
        String de048 = net.safeGet(request, 48);
        log.info("[DMAS-ISS] Reçu 0800 KEY-EXCHANGE {} — DE48={}", keyType, de048);

        String rc = "00";
        try {
            // Parse DE048 : [Type 2c][Len 2c][clé hex][KCV 6hex]
            String typeCode = de048.substring(0, 2);
            int keyLen      = Integer.parseInt(de048.substring(2, 4));
            int hexLen      = keyLen * 2;
            String keyUnderKekHex = de048.substring(4, 4 + hexLen);
            String kcvReceived    = de048.substring(4 + hexLen);

            // Lire kek_under_iss_lmk + kek_clear
            DmasKek kek = kekRepo.findByMemberGroupId(memberGroup)
                    .orElseThrow(() -> new IllegalStateException("KEK introuvable " + memberGroup));

            // Importer la clé sous notre LMK (via KEK clair)
            HsmService.KeyResult imp = hsm.importWorkingKey(keyType, keyUnderKekHex, kek.getKekClear(), keyLen);

            boolean kcvOk = imp.kcv.equalsIgnoreCase(kcvReceived);
            log.info("[DMAS-ISS] {} import — KCV reçu={} calculé={} match={}",
                    keyType, kcvReceived, imp.kcv, kcvOk);

            if (kcvOk) {
                // Stocker dans key_store
                KeyStore ks = keyStoreRepo
                        .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, keyType, "ACTIVE")
                        .orElseGet(KeyStore::new);
                ks.setMemberGroupId(memberGroup);
                ks.setKeyType(keyType);
                ks.setKeyLength(keyLen);
                ks.setEncryptedValue(keyUnderKekHex.length() > 64 ? keyUnderKekHex.substring(0,64) : keyUnderKekHex);
                ks.setKcv(imp.kcv);
                ks.setStatus("ACTIVE");
                ks.setDescription("Imported via DMAS key exchange");
                keyStoreRepo.save(ks);
                log.info("[DMAS-ISS] {} stockée dans key_store (KCV={})", keyType, imp.kcv);
            } else {
                rc = "30"; // format error / KCV mismatch
            }
        } catch (Exception e) {
            log.error("[DMAS-ISS] Key exchange error : {}", e.getMessage(), e);
            rc = "30";
        }

        ISOMsg response = buildResponse(request, rc);
        net.send(out, response);
        log.info("[DMAS-ISS] KEY-EXCHANGE {} -> réponse 0810 DE39={}", keyType, rc);
    }

    private ISOMsg buildResponse(ISOMsg request, String rc) throws Exception {
        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0810");
        if (request.hasField(7))  response.set(7,  request.getString(7));
        if (request.hasField(11)) response.set(11, request.getString(11));
        if (request.hasField(70)) response.set(70, request.getString(70));
        response.set(39, rc);
        return response;
    }
}
