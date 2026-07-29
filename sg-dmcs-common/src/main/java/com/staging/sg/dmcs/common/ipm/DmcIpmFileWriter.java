package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.ISOException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Écrit un fichier IPM RDW dans un répertoire applicatif.
 */
public final class DmcIpmFileWriter {
    private final DmcIpmFileCodec codec;

    public DmcIpmFileWriter(DmcIpmFileCodec codec) {
        this.codec = codec;
    }

    public Path write(Path directory, String fileId,
                      DmcIpmMessageFactory.BuiltFile file)
            throws IOException, ISOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(fileId + ".ipm");
        try (OutputStream output = Files.newOutputStream(target)) {
            codec.write(output, file.messages());
        }
        return target;
    }
}
