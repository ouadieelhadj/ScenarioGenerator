package com.staging.sg.way4aura.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class Way4StagingStoreTest {
    @TempDir Path directory;

    @Test void stagesAtomicallyAndReplaysWithoutDuplicate() throws Exception {
        byte[] xml="<ApplicationFile/>".getBytes(StandardCharsets.UTF_8);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml));
        var store=new Way4StagingStore(directory.toString());
        Path first=store.stage("FP_WAY4_0000000001.xml",xml,hash);
        Path replay=store.stage("FP_WAY4_0000000001.xml",xml,hash);
        assertEquals(first,replay);assertArrayEquals(xml,Files.readAllBytes(first));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }

    @Test void rejectsOverwriteWithDifferentContent() throws Exception {
        byte[] first="first".getBytes(StandardCharsets.UTF_8);byte[] second="second".getBytes(StandardCharsets.UTF_8);
        var store=new Way4StagingStore(directory.toString());
        store.stage("FP_WAY4_0000000002.xml",first,hash(first));
        assertThrows(IllegalStateException.class,()->store.stage("FP_WAY4_0000000002.xml",second,hash(second)));
    }
    private static String hash(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
}
