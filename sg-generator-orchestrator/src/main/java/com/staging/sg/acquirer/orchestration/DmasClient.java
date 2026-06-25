package com.staging.sg.acquirer.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client HTTP vers les modules DMAS (acquéreur 8084, issuer 8501).
 * Gère le login JWT (avec cache du token par base URL) et les POST JSON.
 */
@Component
public class DmasClient {

    private static final Logger log = LoggerFactory.getLogger(DmasClient.class);

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // Cache des tokens par baseUrl
    private final Map<String,String> tokenCache = new ConcurrentHashMap<>();

    /** Login (admin/Admin123!) et met en cache le token pour cette baseUrl. */
    public String login(String baseUrl, String loginName, String password) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            Map<String,String> body = Map.of("login", loginName, "password", password);
            HttpEntity<String> req = new HttpEntity<>(mapper.writeValueAsString(body), h);
            ResponseEntity<String> resp = http.postForEntity(baseUrl + "/auth/login", req, String.class);
            Map<?,?> json = mapper.readValue(resp.getBody(), Map.class);
            String token = String.valueOf(json.get("token"));
            log.info("[DMAS-CLIENT] login OK sur {}", baseUrl);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("Login DMAS échoué sur " + baseUrl + " : " + e.getMessage(), e);
        }
    }

    /** Assure un token valide en cache (login si absent). */
    public String ensureToken(String baseUrl, String loginName, String password) {
        return tokenCache.computeIfAbsent(baseUrl, b -> login(b, loginName, password));
    }

    /** POST JSON authentifié. Renvoie le corps de réponse brut (String). */
    public String postJson(String baseUrl, String path, String token, Object body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.setBearerAuth(token);
            String json = (body instanceof String s) ? s : mapper.writeValueAsString(body);
            HttpEntity<String> req = new HttpEntity<>(json, h);
            ResponseEntity<String> resp = http.postForEntity(baseUrl + path, req, String.class);
            return resp.getBody();
        } catch (Exception e) {
            throw new RuntimeException("POST " + path + " échoué : " + e.getMessage(), e);
        }
    }

    /** GET JSON authentifie. Renvoie le corps de reponse brut (String). */
    public String getJson(String baseUrl, String path, String token) {
        try {
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.setBearerAuth(token);
            org.springframework.http.HttpEntity<Void> req = new org.springframework.http.HttpEntity<>(h);
            org.springframework.http.ResponseEntity<String> resp =
                http.exchange(baseUrl + path, org.springframework.http.HttpMethod.GET, req, String.class);
            return resp.getBody();
        } catch (Exception e) {
            return "{\"error\":\"getJson failed: " + e.getMessage() + "\"}";
        }
    }

    public Map<?,?> parse(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("error", "parse failed: " + e.getMessage(), "raw", String.valueOf(json));
        }
    }
}
