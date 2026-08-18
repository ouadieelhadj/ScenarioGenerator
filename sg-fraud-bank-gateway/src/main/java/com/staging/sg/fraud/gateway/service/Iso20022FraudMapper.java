package com.staging.sg.fraud.gateway.service;

import com.staging.sg.fraud.gateway.api.OmnichannelApi.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;

@Component
public class Iso20022FraudMapper {
    public UniversalTransactionRequest toUniversal(Iso20022EvaluationRequest request){
        Document document=parse(request.document());
        String tx=first(document,"EndToEndId","TxId","InstrId","MsgId");
        String amount=first(document,"InstdAmt","IntrBkSttlmAmt","Amt");
        Element amountElement=firstElement(document,"InstdAmt","IntrBkSttlmAmt","Amt");
        String currency=amountElement==null?"MAD":optionalAttribute(amountElement,"Ccy","MAD");
        String country=optional(document,"Ctry",null,"MAR");
        if(country.length()==2&&"MA".equals(country))country="MAR";
        long amountMinor=decimalMinor(amount);
        String account=optional(document,"IBAN","Id",null);
        String beneficiary=optional(document,"Cdtr","Nm",null);
        return new UniversalTransactionRequest(tx,"TRANSFER","ISO20022","TRANSFER",request.instrumentToken(),null,account,beneficiary,
                null,request.deviceToken(),request.ipToken(),amountMinor,currency,country,"4829",false,true,0,Instant.now(),request.signals());
    }
    private Document parse(String xml){
        try{
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        }catch(Exception e){throw new IllegalArgumentException("Invalid or unsafe ISO 20022 document",e);}
    }
    private String first(Document d,String...names){for(String n:names){Element e=firstElement(d,n);if(e!=null&&!e.getTextContent().isBlank())return e.getTextContent().trim();}throw new IllegalArgumentException("ISO 20022 transaction identifier is required");}
    private String optional(Document d,String first,String second,String fallback){Element e=firstElement(d,first);if(e==null&&second!=null)e=firstElement(d,second);return e==null||e.getTextContent().isBlank()?fallback:e.getTextContent().trim();}
    private Element firstElement(Document d,String...names){for(String n:names){NodeList list=d.getElementsByTagNameNS("*",n);if(list.getLength()>0)return (Element)list.item(0);list=d.getElementsByTagName(n);if(list.getLength()>0)return (Element)list.item(0);}return null;}
    private String optionalAttribute(Element e,String name,String fallback){String value=e.getAttribute(name);return value==null||value.isBlank()?fallback:value;}
    private long decimalMinor(String value){try{return new java.math.BigDecimal(value).movePointRight(2).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();}catch(Exception e){throw new IllegalArgumentException("Invalid ISO 20022 amount",e);}}
}
