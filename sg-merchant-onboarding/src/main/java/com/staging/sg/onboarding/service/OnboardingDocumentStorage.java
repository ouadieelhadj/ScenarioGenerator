package com.staging.sg.onboarding.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingDocumentStorage {
    private static final long MAX_BYTES = 20_000_000;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");
    private static final String PREFIX = "merchant-doc://";

    private final Path root;

    public OnboardingDocumentStorage(
            @Value("${merchant-onboarding.documents.root:${user.dir}/runtime/merchant-onboarding/documents}")
            String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public StoredDocument store(UUID caseId, MultipartFile file) {
        if (caseId == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty document is required");
        }
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported document content type");
        }
        if (file.getSize() < 1 || file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Document size must be between 1 and 20000000 bytes");
        }
        try {
            byte[] content = file.getBytes();
            String objectId = UUID.randomUUID().toString();
            Path caseRoot = root.resolve(caseId.toString()).normalize();
            requireInsideRoot(caseRoot);
            Files.createDirectories(caseRoot);
            Path destination = caseRoot.resolve(objectId + ".bin").normalize();
            requireInsideRoot(destination);
            Files.write(destination, content, StandardOpenOption.CREATE_NEW);
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            return new StoredDocument(PREFIX + caseId + "/" + objectId,
                    contentType, content.length, sha256);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot store onboarding document", exception);
        }
    }

    public Resource load(String storageReference) {
        if (storageReference == null || !storageReference.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown document storage reference");
        }
        String relative = storageReference.substring(PREFIX.length());
        if (!relative.matches("[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid document storage reference");
        }
        Path file = root.resolve(relative + ".bin").toAbsolutePath().normalize();
        requireInsideRoot(file);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Document content not found");
            }
            return resource;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read onboarding document", exception);
        }
    }

    private void requireInsideRoot(Path path) {
        if (!path.startsWith(root)) throw new IllegalArgumentException("Document path is outside storage root");
    }

    public record StoredDocument(String storageReference, String contentType,
            long contentLength, String sha256) {}
}
