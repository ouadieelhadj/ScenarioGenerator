package com.staging.sg.switchlab.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.switchlab.contracts.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SwitchLabClearingService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private final RestClient client;
    private final String visaUrl;
    private final String swamUrl;
    private final String dmcsUrl;
    private final ConcurrentLinkedDeque<SwitchLabClearingArtifact> artifacts = new ConcurrentLinkedDeque<>();

    public SwitchLabClearingService(@Value("${switchlab.clearing.visa-base-url:}") String visaUrl,
                                    @Value("${switchlab.clearing.swam-base-url:}") String swamUrl,
                                    @Value("${switchlab.clearing.dmcs-base-url:}") String dmcsUrl) {
        this.visaUrl=clean(visaUrl); this.swamUrl=clean(swamUrl); this.dmcsUrl=clean(dmcsUrl);
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(10000);
        client=RestClient.builder().requestFactory(factory).build();
    }

    public List<SwitchLabClearingNetwork> networks() {
        return List.of(
                network("VISA_BASE2","Visa Base II","sg-visa-base2-network-simulator",visaUrl,false,false,false,
                        List.of("Controlled artifact adapter absent","ITF unavailable")),
                network("SWAM_LIS","SWAM LIS","sg-swam-lis-switch",swamUrl,true,true,true,List.of()),
                network("MASTERCARD_DMCS","Mastercard DMCS","sg-dmcs-issuer",dmcsUrl,false,true,true,
                        List.of("Path-based import forbidden","Safe evidence download absent")));
    }

    public List<SwitchLabClearingArtifact> artifacts() { return artifacts.stream().limit(100).toList(); }

    public SwitchLabClearingArtifact upload(String networkCode, MultipartFile file, String correlationId) throws Exception {
        if (!"SWAM_LIS".equalsIgnoreCase(networkCode)) throw new IllegalStateException("Controlled upload is unavailable for this network");
        String fileName=safeFileName(file.getOriginalFilename());
        if (file.isEmpty() || file.getSize()>MAX_FILE_SIZE) throw new IllegalArgumentException("Clearing file must be between 1 byte and 10 MB");
        if (!fileName.toLowerCase(Locale.ROOT).matches(".*\\.(lis|dat|txt)$")) throw new IllegalArgumentException("Unsupported clearing file extension");
        MultiValueMap<String,Object> body=new LinkedMultiValueMap<>();
        body.add("file",new ByteArrayResource(file.getBytes()){@Override public String getFilename(){return fileName;}});
        JsonNode response=client.post().uri(required(swamUrl)+"/api/clearing/lis/incoming")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(JsonNode.class);
        String id=UUID.randomUUID().toString();
        SwitchLabClearingArtifact artifact=new SwitchLabClearingArtifact(id,"SWAM_LIS",fileName,
                text(response,"status","RECEIVED"),number(response,"recordCount",number(response,"records",0)),
                text(response,"amountChecksum",null),"artifact://switchlab/clearing/"+id,correlationId,Instant.now());
        artifacts.addFirst(artifact); while(artifacts.size()>200) artifacts.pollLast(); return artifact;
    }

    public SwitchLabClearingEodResult eod(SwitchLabClearingEodRequest request, String correlationId) {
        LocalDate date=request.businessDate()==null?LocalDate.now():request.businessDate();
        String network=request.networkCode()==null?"":request.networkCode().toUpperCase(Locale.ROOT);
        JsonNode response=switch(network){
            case "SWAM_LIS" -> client.post().uri(required(swamUrl)+"/api/clearing/eod?businessDate="+date)
                    .header("X-Operator","SWITCHLAB_BFF").retrieve().body(JsonNode.class);
            case "MASTERCARD_DMCS" -> client.post().uri(required(dmcsUrl)+"/api/dmcs/eod?businessDate="+date)
                    .retrieve().body(JsonNode.class);
            default -> throw new IllegalStateException("EOD is unavailable for this clearing network");
        };
        String id=UUID.randomUUID().toString();
        return new SwitchLabClearingEodResult(id,network,date,text(response,"status","COMPLETED"),
                number(response,"recordCount",number(response,"records",0)),"artifact://switchlab/clearing/eod/"+id,
                correlationId,Instant.now());
    }

    private SwitchLabClearingNetwork network(String code,String label,String module,String url,boolean upload,
                                              boolean eod,boolean disputes,List<String> limits){
        return new SwitchLabClearingNetwork(code,label,module,url.isBlank()?"UNCONFIGURED":"CONFIGURED",upload,eod,disputes,limits);
    }
    private String safeFileName(String name){if(name==null||name.isBlank())throw new IllegalArgumentException("File name is required");String safe=java.nio.file.Path.of(name).getFileName().toString();if(!safe.equals(name)||safe.contains(".."))throw new IllegalArgumentException("Invalid file name");return safe;}
    private String required(String url){if(url.isBlank())throw new IllegalStateException("Clearing adapter is not configured");return url;}
    private String text(JsonNode body,String field,String fallback){return body!=null&&body.hasNonNull(field)?body.get(field).asText():fallback;}
    private long number(JsonNode body,String field,long fallback){return body!=null&&body.has(field)?body.get(field).asLong(fallback):fallback;}
    private static String clean(String value){if(value==null||value.isBlank())return "";return value.endsWith("/")?value.substring(0,value.length()-1):value;}
}
