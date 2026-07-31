package com.staging.sg.card.issuing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="issuing_authorization_event")
public class AuthorizationEvent {
    @Id private UUID id;
    @Column(name="authorization_id",nullable=false,updatable=false) private UUID authorizationId;
    @Column(name="event_type",nullable=false,length=64,updatable=false) private String eventType;
    @Column(name="correlation_id",nullable=false,length=128,updatable=false) private String correlationId;
    @Column(name="details_json",nullable=false,columnDefinition="TEXT",updatable=false) private String detailsJson;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected AuthorizationEvent(){}
    public static AuthorizationEvent recorded(UUID authorizationId,String eventType,String correlationId,String detailsJson){
        if(authorizationId==null||blank(eventType)||blank(correlationId)||blank(detailsJson))
            throw new IllegalArgumentException("Invalid authorization event");
        AuthorizationEvent value=new AuthorizationEvent(); value.id=UUID.randomUUID();
        value.authorizationId=authorizationId; value.eventType=eventType;
        value.correlationId=correlationId; value.detailsJson=detailsJson;
        value.createdAt=Instant.now(); return value;
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
