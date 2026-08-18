package com.staging.sg.fraud.service;
import com.staging.sg.fraud.api.FraudApi.ScoreRequest;
public record FraudSubjectIdentity(String sectorId,String subjectType,String subjectReference){
    public static FraudSubjectIdentity resolve(ScoreRequest request){
        String sector=FraudSector.normalize(request.sector(),request.channel());String channel=request.channel().toUpperCase();
        if(present(request.subjectReference()))return new FraudSubjectIdentity(sector,request.subjectType(),request.subjectReference());
        if("MOBILE_BANKING".equals(sector)){if(present(request.accountReference()))return new FraudSubjectIdentity(sector,"ACCOUNT",request.accountReference());if(present(request.customerReference()))return new FraudSubjectIdentity(sector,"CUSTOMER",request.customerReference());return new FraudSubjectIdentity(sector,"WALLET",request.tokenReference());}
        if(channel.contains("WALLET"))return new FraudSubjectIdentity(sector,"WALLET",request.tokenReference());
        if(!"MONETIQUE".equals(sector)&&present(request.accountReference()))return new FraudSubjectIdentity(sector,"ACCOUNT",request.accountReference());
        return new FraudSubjectIdentity(sector,"CARD_TOKEN",request.tokenReference());
    }
    private static boolean present(String value){return value!=null&&!value.isBlank();}
}
