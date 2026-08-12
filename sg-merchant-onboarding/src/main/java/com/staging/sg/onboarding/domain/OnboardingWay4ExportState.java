package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="onboarding_way4_export_state")
public class OnboardingWay4ExportState {
    @Id @Column(name="case_id") private UUID caseId;
    @Column(name="application_reg_number",nullable=false,length=96,unique=true,updatable=false) private String applicationRegNumber;
    @Column(name="connector_file_id") private UUID connectorFileId;
    @Column(nullable=false,length=32) private String status;
    @Column(name="way4_client_id",length=160) private String way4ClientId;
    @Column(name="merchant_contract_number",length=160) private String merchantContractNumber;
    @Column(name="mid",length=64) private String mid;
    @Column(name="tids_json",columnDefinition="TEXT") private String tidsJson;
    @Column(name="return_file_name",length=255) private String returnFileName;
    @Column(name="last_error_code",length=64) private String lastErrorCode;
    @Column(name="last_error_message",length=1000) private String lastErrorMessage;
    @Column(name="last_failure_retryable") private Boolean lastFailureRetryable;
    @Column(name="failed_at") private Instant failedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected OnboardingWay4ExportState(){}
    public static OnboardingWay4ExportState pending(UUID caseId,String reg){var v=new OnboardingWay4ExportState();v.caseId=caseId;v.applicationRegNumber=reg;v.status="PENDING";v.createdAt=Instant.now();v.updatedAt=v.createdAt;return v;}
    public void generated(UUID fileId){this.connectorFileId=fileId;this.status="GENERATED";this.lastErrorCode=null;this.lastErrorMessage=null;this.lastFailureRetryable=null;this.failedAt=null;this.updatedAt=Instant.now();}
    public void failed(String code,String message,boolean retryable){this.status=retryable?"PENDING":"REJECTED";this.lastErrorCode=clean(code,64);this.lastErrorMessage=clean(message,1000);this.lastFailureRetryable=retryable;this.failedAt=Instant.now();this.updatedAt=this.failedAt;}
    private static String clean(String value,int max){if(value==null||value.isBlank())return null;String result=value.replaceAll("[\\r\\n\\t]+"," ").trim();return result.length()<=max?result:result.substring(0,max);}
    public UUID caseId(){return caseId;} public String applicationRegNumber(){return applicationRegNumber;}
    public String status(){return status;} public String lastErrorCode(){return lastErrorCode;}
    public String lastErrorMessage(){return lastErrorMessage;} public Boolean lastFailureRetryable(){return lastFailureRetryable;}
    public UUID connectorFileId(){return connectorFileId;} public Instant updatedAt(){return updatedAt;}
}
