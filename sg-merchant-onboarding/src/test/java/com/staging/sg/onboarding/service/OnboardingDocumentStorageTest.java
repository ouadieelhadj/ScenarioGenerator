package com.staging.sg.onboarding.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnboardingDocumentStorageTest {
    @TempDir Path root;

    @Test
    void storesContentUnderOpaqueReferenceAndReturnsItsServerDigest() throws Exception {
        OnboardingDocumentStorage storage = new OnboardingDocumentStorage(root.toString());
        byte[] content = "proof-document".getBytes();
        var stored = storage.store(UUID.randomUUID(), new MockMultipartFile(
                "file", "proof.pdf", "application/pdf", content));

        assertThat(stored.storageReference()).startsWith("merchant-doc://");
        assertThat(stored.contentLength()).isEqualTo(content.length);
        assertThat(stored.sha256()).hasSize(64);
        assertThat(storage.load(stored.storageReference()).getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void rejectsUnsupportedOrOversizedMetadataBeforePersistence() {
        OnboardingDocumentStorage storage = new OnboardingDocumentStorage(root.toString());
        var file = new MockMultipartFile("file", "script.html", "text/html", "bad".getBytes());
        assertThatThrownBy(() -> storage.store(UUID.randomUUID(), file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }
}
