package com.staging.sg.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Injecte le port REST (server.port) depuis la table `networks` AVANT que
 * Tomcat ne demarre.
 *
 * POURQUOI un EnvironmentPostProcessor : Spring Boot resout `server.port`
 * pendant le refresh du contexte, donc AVANT que la datasource JPA existe.
 * On ouvre ici une connexion JDBC brute, tres tot dans le cycle de boot.
 *
 * Chaque module declare dans son application.properties / .yml :
 *
 *     sg.network.code=MASTERCARD_SMS      (ou SWAM, DMAS...)
 *     sg.network.role=ISSUER              (ou ACQUIRER)
 *
 * Mapping vers les colonnes de `networks` :
 *     ACQUIRER -> acquirer_rest_port
 *     ISSUER   -> issuer_rest_port
 *
 * COMPORTEMENT SI LA BASE EST INJOIGNABLE : le module NE DEMARRE PAS.
 * C'est volontaire — la base est la source de verite des ports, on ne veut
 * pas d'un module qui se lance sur un port different de ce qui est declare.
 *
 * Pour desactiver (tests unitaires, demarrage de secours) :
 *     sg.network.port-from-db=false
 */
public class NetworkPortEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE_NAME = "sgNetworkPorts";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {

        // Desactivation explicite
        String enabled = env.getProperty("sg.network.port-from-db", "true");
        if (!"true".equalsIgnoreCase(enabled)) {
            log("sg.network.port-from-db=false -> server.port lu depuis la config locale");
            return;
        }

        String code = env.getProperty("sg.network.code");
        String role = env.getProperty("sg.network.role");

        // Module qui ne declare rien : on ne fait rien (ex. orchestrateur)
        if (code == null || role == null) return;

        String url  = env.getProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username");
        String pass = env.getProperty("spring.datasource.password");

        if (url == null || user == null) {
            throw new IllegalStateException(
                    "[SG-PORTS] spring.datasource.url/username absents — impossible de lire "
                  + "les ports depuis `networks`. Renseigner la datasource ou poser "
                  + "sg.network.port-from-db=false.");
        }

        // ------------------------------------------------------------------
        //  DMAS multi-banque : --sg.interface prime sur la table networks
        //
        //  Chaque module recoit un seul parametre au demarrage et lit toute
        //  sa configuration dans mc_dmas_interface :
        //      java -jar sg-mc-dmas-member.jar --sg.interface=DMAS_BANK_A
        //
        //  Sans ce parametre, on retombe sur le chemin historique (table
        //  networks), inchange pour SWAM et MC SMS.
        // ------------------------------------------------------------------
        String iface = env.getProperty("sg.interface");
        if (iface != null && !iface.isBlank()) {
            String firstIface = iface.split(",")[0].trim();
            String interfaceTable = "SWAM".equalsIgnoreCase(code)
                    ? "swam_interface" : "mc_dmas_interface";
            InterfaceBootConfig boot = readInterfaceBootConfig(
                    url, user, pass, interfaceTable, firstIface);
            Integer ifacePort = boot.restPort();
            if (ifacePort == null) {
                throw new IllegalStateException(
                        "[SG-PORTS] rest_port est NULL dans " + interfaceTable + " pour "
                      + "id_interface='" + iface + "'");
            }
            Map<String, Object> ifaceProps = new HashMap<>();
            ifaceProps.put("server.port", ifacePort);
            if (boot.logFile() == null || boot.logFile().isBlank()) {
                throw new IllegalStateException(
                        "[SG-PORTS] log_file est NULL dans " + interfaceTable + " pour "
                      + "id_interface='" + iface + "'");
            }
            ifaceProps.put("logging.file.name", boot.logFile());
            env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, ifaceProps));
            log("server.port=" + ifacePort
              + ", logging.file.name=" + boot.logFile()
              + " (" + interfaceTable + " pour id_interface=" + firstIface + ")");
            return;
        }

        String column = switch (role.toUpperCase()) {
            case "ACQUIRER" -> "acquirer_rest_port";
            case "ISSUER"   -> "issuer_rest_port";
            default -> throw new IllegalStateException(
                    "[SG-PORTS] sg.network.role invalide : '" + role
                  + "' (attendu ACQUIRER ou ISSUER)");
        };

        Integer port = readPort(url, user, pass, column, code);

        if (port == null) {
            throw new IllegalStateException(
                    "[SG-PORTS] " + column + " est NULL en base pour networks.code='"
                  + code + "'. Renseigner la colonne avant de demarrer le module.");
        }

        Map<String, Object> props = new HashMap<>();
        props.put("server.port", port);
        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));

        log("server.port=" + port + " (networks." + column + " pour code=" + code + ")");
    }

    /** Connexion JDBC brute. Toute erreur remonte : le module ne doit pas demarrer. */
    private Integer readPort(String url, String user, String pass,
                             String column, String code) {
        String sql = "SELECT " + column + " FROM networks WHERE code = ?";
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "[SG-PORTS] Aucune ligne dans `networks` pour code='" + code + "'");
                }
                int p = rs.getInt(1);
                return rs.wasNull() ? null : p;
            }

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[SG-PORTS] Base injoignable — demarrage annule. "
                  + "Les ports sont pilotes par la table `networks`, PostgreSQL doit etre "
                  + "demarre. Detail : " + e.getMessage(), e);
        }
    }

    /**
     * Port REST depuis mc_dmas_interface (DMAS multi-banque).
     * L'identifiant vient du parametre --sg.interface.
     */
    private InterfaceBootConfig readInterfaceBootConfig(
            String url, String user, String pass, String table, String iface) {
        String sql = "SELECT rest_port, log_file FROM " + table
                   + " WHERE id_interface = ?";
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, iface);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "[SG-PORTS] Aucune ligne dans " + table + " pour "
                          + "id_interface='" + iface + "'. Verifier --sg.interface");
                }
                int p = rs.getInt(1);
                Integer port = rs.wasNull() ? null : p;
                return new InterfaceBootConfig(port, rs.getString(2));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[SG-PORTS] Base injoignable — demarrage annule. Detail : "
                  + e.getMessage(), e);
        }
    }

    private record InterfaceBootConfig(Integer restPort, String logFile) {}

    /** Le logger Spring n'existe pas encore a ce stade du boot : sortie console. */
    private void log(String msg) {
        System.out.println("[SG-PORTS] " + msg);
    }

    @Override
    public int getOrder() {
        // Apres le chargement de application.properties / application.yml
        return Ordered.LOWEST_PRECEDENCE;
    }
}
