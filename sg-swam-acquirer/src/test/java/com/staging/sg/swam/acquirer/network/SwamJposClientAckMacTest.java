package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.iso.SwamLengthChannel;
import com.staging.sg.common.service.SwamInterfaceService;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SwamJposClientAckMacTest {

    @Test
    void appliesMacBeforeSendingPekAcknowledgement811() throws Exception {
        assertMacBeforeSend("811");
    }

    @Test
    void appliesMacBeforeSendingMakAcknowledgement899() throws Exception {
        assertMacBeforeSend("899");
    }

    @Test
    void refusesToSendAcknowledgementWhenNoMacKeyIsAvailable()
            throws Exception {
        SwamJposClient client =
                new SwamJposClient(mock(SwamInterfaceService.class));
        SwamMac swamMac = mock(SwamMac.class);
        SwamLengthChannel channel = mock(SwamLengthChannel.class);
        ReflectionTestUtils.setField(client, "swamMac", swamMac);
        ReflectionTestUtils.setField(client, "channel", channel);
        ISOMsg request = new ISOMsg();
        request.setMTI("1804");
        request.set(11, "123456");
        request.set(24, "811");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        client, "sendAck", request, "800"));

        assertTrue(error.getMessage().contains("sans DE128"));
        verifyNoInteractions(channel);
    }

    private static void assertMacBeforeSend(String functionCode)
            throws Exception {
        SwamJposClient client =
                new SwamJposClient(mock(SwamInterfaceService.class));
        SwamMac swamMac = mock(SwamMac.class);
        SwamLengthChannel channel = mock(SwamLengthChannel.class);
        byte[] expectedMac = new byte[] {0x01, 0x02, 0x03, 0x04};

        doAnswer(invocation -> {
            ISOMsg ack = invocation.getArgument(0);
            ack.set(128, expectedMac);
            return "01020304";
        }).when(swamMac).apply(any(ISOMsg.class));

        ReflectionTestUtils.setField(client, "swamMac", swamMac);
        ReflectionTestUtils.setField(client, "channel", channel);

        ISOMsg request = new ISOMsg();
        request.setMTI("1804");
        request.set(11, "123456");
        request.set(24, functionCode);

        ReflectionTestUtils.invokeMethod(client, "sendAck", request, "800");

        InOrder order = inOrder(swamMac, channel);
        order.verify(swamMac).apply(any(ISOMsg.class));
        ArgumentCaptor<ISOMsg> captor = ArgumentCaptor.forClass(ISOMsg.class);
        order.verify(channel).send(captor.capture());
        ISOMsg ack = captor.getValue();
        assertEquals("1814", ack.getMTI());
        assertEquals(functionCode, ack.getString(24));
        assertEquals("800", ack.getString(39));
        assertTrue(ack.hasField(128));
        assertArrayEquals(expectedMac, ack.getBytes(128));
    }
}
