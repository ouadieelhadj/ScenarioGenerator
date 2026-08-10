package com.staging.sg.acquiring.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import java.time.Instant;
import java.util.Map;

@Component
public class HttpWay4ConnectorAdapter implements Way4ConnectorPort {
    private final boolean enabled; private final RestClient client; private final RestClient tokenClient;
    private final String tokenUrl, clientId, clientSecret, scope; private volatile Token token;
    public HttpWay4ConnectorAdapter(@Value("${acquiring.way4.enabled:false}") boolean enabled,
            @Value("${acquiring.way4.base-url:http://127.0.0.1:8580}") String baseUrl,
            @Value("${acquiring.way4.oauth2.token-url:}") String tokenUrl,
            @Value("${acquiring.way4.oauth2.client-id:}") String clientId,
            @Value("${acquiring.way4.oauth2.client-secret:}") String clientSecret,
            @Value("${acquiring.way4.oauth2.scope:way4.generate}") String scope) {
        this.enabled=enabled; this.client=RestClient.builder().baseUrl(baseUrl).build(); this.tokenClient=RestClient.create();
        this.tokenUrl=tokenUrl; this.clientId=clientId; this.clientSecret=clientSecret; this.scope=scope;
    }
    @Override public Result generate(Way4ExportRequest request) {
        if (!enabled) throw new Way4ConnectorException("WAY4 connector dispatch is disabled", false, true, null);
        try {
            Map<?,?> response=client.post().uri("/api/internal/way4-aura/v1/dry-runs")
                    .contentType(MediaType.APPLICATION_JSON).headers(h->h.setBearerAuth(accessToken()))
                    .body(request).retrieve().body(Map.class);
            if(response==null || response.get("fileId")==null) throw new Way4ConnectorException("Connector returned no file",true,false,null);
            return new Result(java.util.UUID.fromString(response.get("fileId").toString()), String.valueOf(response.get("status")));
        } catch (Way4ConnectorException e) { throw e; }
        catch (HttpStatusCodeException e) { boolean mapping=e.getStatusCode().value()==422;
            boolean retry=e.getStatusCode().is5xxServerError()||e.getStatusCode().value()==429;
            throw new Way4ConnectorException("WAY4 connector HTTP status "+e.getStatusCode().value(),retry,mapping,e); }
        catch (ResourceAccessException e) { throw new Way4ConnectorException("WAY4 connector unreachable",true,false,e); }
    }
    private String accessToken() {
        Token current=token; if(current!=null&&current.expiresAt.isAfter(Instant.now().plusSeconds(30))) return current.value;
        synchronized(this) { current=token; if(current!=null&&current.expiresAt.isAfter(Instant.now().plusSeconds(30))) return current.value;
            if(tokenUrl.isBlank()||clientId.isBlank()||clientSecret.isBlank()) throw new Way4ConnectorException("WAY4 OAuth2 credentials are missing",false,true,null);
            var form=new LinkedMultiValueMap<String,String>(); form.add("grant_type","client_credentials"); form.add("client_id",clientId); form.add("client_secret",clientSecret); form.add("scope",scope);
            Map<?,?> response=tokenClient.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            if(response==null||response.get("access_token")==null) throw new Way4ConnectorException("OAuth2 server returned no access token",true,false,null);
            long seconds=response.get("expires_in") instanceof Number n?n.longValue():300; token=new Token(response.get("access_token").toString(),Instant.now().plusSeconds(Math.max(60,seconds))); return token.value; }
    }
    private record Token(String value, Instant expiresAt) {}
}
