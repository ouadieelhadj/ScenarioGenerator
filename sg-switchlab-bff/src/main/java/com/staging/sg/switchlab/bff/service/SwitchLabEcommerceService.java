package com.staging.sg.switchlab.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.switchlab.contracts.SwitchLabEcommerceComponent;
import com.staging.sg.switchlab.contracts.SwitchLabEcommerceScenario;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchLabEcommerceService {
    private final RestClient client; private final String merchantUrl; private final String gatewayUrl; private final String threeDsUrl;
    public SwitchLabEcommerceService(@Value("${switchlab.ecommerce.merchant-base-url:http://localhost:8551}")String merchantUrl,
            @Value("${switchlab.ecommerce.gateway-base-url:http://localhost:8563}")String gatewayUrl,
            @Value("${switchlab.ecommerce.three-ds-base-url:http://localhost:8561}")String threeDsUrl){
        this.merchantUrl=clean(merchantUrl);this.gatewayUrl=clean(gatewayUrl);this.threeDsUrl=clean(threeDsUrl);
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(1500);factory.setReadTimeout(3000);client=RestClient.builder().requestFactory(factory).build();}
    public List<SwitchLabEcommerceComponent> components(){return List.of(
        component("MERCHANT_SITE","Site marchand","sg-merchant-site-simulator",merchantUrl,"/api/merchant-site-simulator/v1/health",List.of("ORDERS","CHECKOUT","3DS"),List.of("Payment API requires clear card data")),
        component("CARD_GATEWAY","Gateway Visa/Mastercard","sg-visa-mastercard-gateway-simulator",gatewayUrl,"/api/routing/v1/health",List.of("VISA","MASTERCARD","AUTHORIZATION"),List.of("Secure card reference resolver absent")),
        component("THREE_DS_NETWORK","Réseau 3DS","sg-3ds-network-simulator",threeDsUrl,"/api/3ds/network/v1/health",List.of("AREQ","CREQ","RREQ","FRICTIONLESS","CHALLENGE"),List.of("Sandbox display exposes OTP and is blocked")));
    }
    public List<SwitchLabEcommerceScenario> scenarios(){return List.of(
        blocked("ECOM.VISA.FRICTIONLESS","Visa frictionless","VISA","FRICTIONLESS"),
        blocked("ECOM.VISA.CHALLENGE","Visa challenge","VISA","CHALLENGE"),
        blocked("ECOM.MC.FRICTIONLESS","Mastercard frictionless","MASTERCARD","FRICTIONLESS"),
        blocked("ECOM.MC.CHALLENGE","Mastercard challenge","MASTERCARD","CHALLENGE"));}
    private SwitchLabEcommerceComponent component(String code,String label,String module,String base,String health,List<String>caps,List<String>limits){
        return new SwitchLabEcommerceComponent(code,label,module,probe(base+health),caps,limits);}
    private String probe(String url){try{JsonNode body=client.get().uri(url).retrieve().body(JsonNode.class);return body!=null&&body.has("status")?body.get("status").asText("UP"):"UP";}catch(RuntimeException unavailable){return "DOWN";}}
    private SwitchLabEcommerceScenario blocked(String code,String label,String program,String flow){return new SwitchLabEcommerceScenario(code,label,program,flow,false,"Server-side sensitive data resolver absent");}
    private static String clean(String value){return value.endsWith("/")?value.substring(0,value.length()-1):value;}
}
