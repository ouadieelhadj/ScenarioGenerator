package com.staging.sg.acquirer.config;

import com.staging.sg.acquirer.SgAcquirerApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class RestartService {

    private static final Logger log = LoggerFactory.getLogger(RestartService.class);
    private static final String YML_PATH = "src/main/resources/application.yml";

    private final ApplicationContext context;

    public RestartService(ApplicationContext context) {
        this.context = context;
    }

    public void changePortAndRestart(int newPort) {
        persistPort(newPort);
        log.info("[CONFIG] Port change vers {} — redemarrage dans 1.5s", newPort);

        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) context;
        String[] originalArgs = ctx.getBean(ApplicationArguments.class).getSourceArgs();

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1500);
                log.info("[RESTART] Fermeture du contexte...");
                ctx.close();

                // IMPORTANT : retirer tout --server.port existant pour eviter l'accumulation
                // (sinon on obtient server.port=8090,8080 au 2e restart)
                List<String> cleaned = new ArrayList<>();
                for (String a : originalArgs) {
                    if (!a.startsWith("--server.port=")) {
                        cleaned.add(a);
                    }
                }
                cleaned.add("--server.port=" + newPort);

                log.info("[RESTART] Relance sur le port {}...", newPort);
                SpringApplication.run(SgAcquirerApplication.class, cleaned.toArray(new String[0]));
                log.info("[RESTART] Relance sur le port {} terminee", newPort);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "port-restart");
        t.setDaemon(false);
        t.start();
    }

    private void persistPort(int newPort) {
        try {
            Path path = Paths.get(YML_PATH);
            if (!Files.exists(path)) {
                log.warn("[CONFIG] yml introuvable a {} — port non persiste", YML_PATH);
                return;
            }
            List<String> lines = Files.readAllLines(path);
            boolean inServer = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.stripTrailing().equals("server:")) { inServer = true; continue; }
                if (inServer && line.matches("\\s*port:\\s*\\d+\\s*")) {
                    String indent = line.substring(0, line.indexOf("port:"));
                    lines.set(i, indent + "port: " + newPort);
                    log.info("[CONFIG] Port {} persiste dans {}", newPort, YML_PATH);
                    break;
                }
                if (inServer && !line.startsWith(" ") && !line.isBlank()) inServer = false;
            }
            Files.write(path, lines);
        } catch (IOException e) {
            log.error("[CONFIG] Echec ecriture yml : {}", e.getMessage());
        }
    }
}

