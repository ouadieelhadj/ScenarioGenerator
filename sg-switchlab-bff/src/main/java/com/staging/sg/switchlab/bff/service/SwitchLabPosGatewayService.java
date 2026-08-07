package com.staging.sg.switchlab.bff.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchLabPosGatewayService {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private final String baseUrl;
    private final RestClient restClient;

    public SwitchLabPosGatewayService(@Value("${switchlab.pos.base-url:http://localhost:8532}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(60_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public Map<String, Object> post(String path, Object body) {
        RestClient.RequestBodySpec request = restClient.method(HttpMethod.POST).uri(baseUrl + path);
        if (body != null) request.body(body);
        Map<String, Object> result = request.retrieve().body(MAP);
        return result == null ? Map.of() : result;
    }

    public Map<String, Object> post(String path) {
        Map<String, Object> result = restClient.post().uri(baseUrl + path).retrieve().body(MAP);
        return result == null ? Map.of() : result;
    }
}
