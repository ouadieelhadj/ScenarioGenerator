package com.staging.sg.fraud.gateway.network;

import com.staging.sg.fraud.gateway.service.PermanentIsoMessageProcessor;
import org.jpos.iso.*;import org.jpos.iso.channel.NACChannel;import org.jpos.iso.packager.ISO87APackager;
import org.junit.jupiter.api.Test;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.mock.mockito.MockBean;
import static org.assertj.core.api.Assertions.*;import static org.mockito.ArgumentMatchers.any;import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"fraud-gateway.iso.permanent-enabled=true","fraud-gateway.iso.listen-port=18572","fraud-gateway.iso.client-enabled=false"})
class PermanentIso8583ServerIntegrationTest{
 @MockBean PermanentIsoMessageProcessor processor;
 @Test void keepsSameTcpSessionForSignOnAndEcho()throws Exception{
  when(processor.process(any(ISOMsg.class))).thenAnswer(call->{ISOMsg req=call.getArgument(0);ISOMsg out=new ISOMsg();out.setPackager(req.getPackager());out.setMTI("0810");out.set(11,req.getString(11));out.set(39,"00");out.set(70,req.getString(70));return out;});
  NACChannel channel=new NACChannel("127.0.0.1",18572,new ISO87APackager(),null);channel.connect();
  try{channel.send(network("000001","001"));assertThat(channel.receive().getString(39)).isEqualTo("00");assertThat(channel.isConnected()).isTrue();channel.send(network("000002","301"));assertThat(channel.receive().getString(70)).isEqualTo("301");assertThat(channel.isConnected()).isTrue();}finally{channel.disconnect();}
 }
 private ISOMsg network(String stan,String function)throws Exception{ISOMsg m=new ISOMsg();m.setPackager(new ISO87APackager());m.setMTI("0800");m.set(7,"0815031200");m.set(11,stan);m.set(70,function);return m;}
}
