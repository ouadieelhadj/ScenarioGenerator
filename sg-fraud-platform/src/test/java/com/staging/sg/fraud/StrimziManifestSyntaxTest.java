package com.staging.sg.fraud;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StrimziManifestSyntaxTest {
    @Test
    void productionManifestsAreValidYamlAndContainSecurityControls() throws Exception {
        Path directory = Path.of("..", "deployment", "fraud", "kafka", "strimzi", "production");
        try (Stream<Path> stream = Files.list(directory)) {
            var manifests = stream.filter(path -> path.toString().endsWith(".yaml")).sorted().toList();
            assertEquals(6, manifests.size());
            for (Path manifest : manifests) {
                try (Reader reader = Files.newBufferedReader(manifest)) {
                    int documents = 0;
                    for (Object document : new Yaml().loadAll(reader)) {
                        assertNotNull(document, manifest + " must not contain an empty YAML document");
                        documents++;
                    }
                    assertTrue(documents > 0, manifest + " must contain YAML");
                }
            }
        }
        String kafka = Files.readString(directory.resolve("02-kafka.yaml"));
        assertAll(
                () -> assertTrue(kafka.contains("type: custom")),
                () -> assertTrue(kafka.contains("OAUTHBEARER")),
                () -> assertTrue(kafka.contains("tls: true")),
                () -> assertTrue(kafka.contains("auto.create.topics.enable: \"false\"")),
                () -> assertFalse(kafka.contains("client.secret")));
        String patch = Files.readString(directory.resolve("05-fraud-platform-kafka-oauth.patch.yaml"));
        assertFalse(patch.contains("stringData:"), "Secrets must never be embedded in deployment manifests");
    }
}
