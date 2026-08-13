package com.staging.sg.way4aura.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HexFormat;
import java.security.MessageDigest;

@Component
public class Way4StagingStore {
    private final String configuredDirectory;

    public Way4StagingStore(@Value("${way4-aura.staging-directory:}") String configuredDirectory) {
        this.configuredDirectory = configuredDirectory;
    }

    public Path stage(String fileName, byte[] xml, String expectedSha256) {
        if (configuredDirectory == null || configuredDirectory.isBlank())
            throw new AuraMappingBlockedException("WAY4 staging directory is not configured");
        if (fileName == null || !(fileName.matches("xadvapl[A-Za-z0-9]{1,32}_\\d{5}\\.\\d{3}")
                || fileName.matches("FP_WAY4_\\d{10}\\.xml")))
            throw new IllegalArgumentException("Invalid WAY4 staging file name");
        try {
            Path directory = Path.of(configuredDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName).normalize();
            if (!target.getParent().equals(directory)) throw new IllegalArgumentException("Invalid WAY4 staging path");
            if (Files.exists(target)) {
                if (!sha256(Files.readAllBytes(target)).equals(expectedSha256))
                    throw new IllegalStateException("STAGING_CONFLICT: existing file has different content");
                return target;
            }
            Path temporary = Files.createTempFile(directory, fileName + ".", ".tmp");
            try {
                Files.write(temporary, xml, StandardOpenOption.TRUNCATE_EXISTING);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
                if (!sha256(Files.readAllBytes(temporary)).equals(expectedSha256))
                    throw new IllegalStateException("STAGING_HASH_MISMATCH");
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target); }
                return target;
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot stage WAY4 XML file", exception);
        }
    }

    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
