package com.staging.sg.onboarding.integration;

import com.staging.sg.onboarding.port.*;
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
    private final String tokenUrl; private final String clientId; private final String clientSecret; private final String scope;
    private volatile Token cachedToken;
    public HttpWay4ConnectorAdapter(
            @Value("${merchant-onboarding.way4-connector.enabled:false}") boolean enabled,
            @Value("${merchant-onboarding.way4-connector.base-url:http://127.0.0.1:8580}") String baseUrl,
            @Value("${merchant-onboarding.way4-connector.oauth2.token-url:}") String tokenUrl,
            @Value("${merchant-onboarding.way4-connector.oauth2.client-id:}") String clientId,
            @Value("${merchant-onboarding.way4-connector.oauth2.client-secret:}") String clientSecret,
            @Value("${merchant-onboarding.way4-connector.oauth2.scope:way4.generate}") String scope) {
        this.enabled=enabled; this.client=RestClient.builder().baseUrl(baseUrl).build(); this.tokenClient=RestClient.create();
        this.tokenUrl=tokenUrl; this.clientId=clientId; this.clientSecret=clientSecret; this.scope=scope;
    }
    @Override public Result generate(PortalWay4ExportCommand command, String correlationId) {
        if(!enabled) throw new Way4ConnectorTransportException("WAY4 connector dispatch is disabled",true,null);
        try {
            Result result=client.post().uri("/api/internal/way4-aura/v1/dry-runs")
                    .contentType(MediaType.APPLICATION_JSON).headers(h->h.setBearerAuth(accessToken()))
                    .header("Idempotency-Key",command.idempotencyKey()).header("X-Correlation-ID",correlationId)
                    .body(command).retrieve().body(Result.class);
            if(result==null || result.fileId()==null) throw new Way4ConnectorTransportException("WAY4 connector returned an empty result",true,null);
            return result;
        } catch(Way4ConnectorTransportException e){throw e;}
        catch(HttpStatusCodeException e){boolean retry=e.getStatusCode().is5xxServerError()||e.getStatusCode().value()==429;
            throw new Way4ConnectorTransportException("WAY4 connector HTTP status "+e.getStatusCode().value(),retry,e);}
        catch(ResourceAccessException e){throw new Way4ConnectorTransportException("WAY4 connector is temporarily unreachable",true,e);}
    }
    private String accessToken(){Token token=cachedToken; if(token!=null&&token.expiresAt().isAfter(Instant.now().plusSeconds(30)))return token.value();
        synchronized(this){token=cachedToken;if(token!=null&&token.expiresAt().isAfter(Instant.now().plusSeconds(30)))return token.value();
            if(tokenUrl.isBlank()||clientId.isBlank()||clientSecret.isBlank())throw new Way4ConnectorTransportException("WAY4 OAuth2 client credentials are not configured",false,null);
            var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");form.add("client_id",clientId);form.add("client_secret",clientSecret);form.add("scope",scope);
            @SuppressWarnings("unchecked") Map<String,Object> response=tokenClient.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            if(response==null||!(response.get("access_token") instanceof String value)||value.isBlank())throw new Way4ConnectorTransportException("OAuth2 server returned no access token",true,null);
            long ttl=response.get("expires_in") instanceof Number n?n.longValue():300L;cachedToken=new Token(value,Instant.now().plusSeconds(Math.max(60,ttl)));return value;}}
    private record Token(String value,Instant expiresAt){}
}
