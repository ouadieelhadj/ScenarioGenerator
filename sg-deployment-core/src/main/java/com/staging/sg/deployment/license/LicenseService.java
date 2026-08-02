package com.staging.sg.deployment.license;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.staging.sg.deployment.model.DeploymentManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

public final class LicenseService {
    public static final String ALGORITHM = "SHA256withRSA";
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de générer la paire de clés de licence", exception);
        }
    }

    public void writePrivateKey(Path path, PrivateKey key) throws IOException {
        writePem(path, "PRIVATE KEY", key.getEncoded());
    }

    public void writePublicKey(Path path, PublicKey key) throws IOException {
        writePem(path, "PUBLIC KEY", key.getEncoded());
    }

    public SignedLicenseEnvelope issue(TechnicalLicense license, PrivateKey privateKey, Path output) throws IOException {
        try {
            byte[] payload = mapper.writeValueAsBytes(normalize(license));
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(payload);
            SignedLicenseEnvelope envelope = new SignedLicenseEnvelope(ALGORITHM,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(payload),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), envelope);
            return envelope;
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de signer la licence", exception);
        }
    }

    public TechnicalLicense verify(Path envelopePath, Path publicKeyPath) throws IOException {
        try {
            SignedLicenseEnvelope envelope = mapper.readValue(envelopePath.toFile(), SignedLicenseEnvelope.class);
            if (!ALGORITHM.equals(envelope.algorithm())) {
                throw new IllegalArgumentException("Algorithme de signature non autorisé");
            }
            byte[] payload = Base64.getUrlDecoder().decode(envelope.payload());
            byte[] signature = Base64.getUrlDecoder().decode(envelope.signature());
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(readPublicKey(publicKeyPath));
            verifier.update(payload);
            if (!verifier.verify(signature)) throw new IllegalArgumentException("Signature de licence invalide");
            TechnicalLicense license = mapper.readValue(payload, TechnicalLicense.class);
            LocalDate today = LocalDate.now();
            if (today.isBefore(LocalDate.parse(license.validFrom()))
                    || today.isAfter(LocalDate.parse(license.validUntil()))) {
                throw new IllegalArgumentException("Licence hors période de validité");
            }
            return license;
        } catch (IOException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de vérifier la licence", exception);
        }
    }

    public void requireMatches(TechnicalLicense license, DeploymentManifest manifest) {
        if (!license.clientCode().equals(manifest.clientCode())) {
            throw new IllegalArgumentException("La licence ne correspond pas au client");
        }
        if (!license.environmentCode().equals(manifest.environmentCode())) {
            throw new IllegalArgumentException("La licence ne correspond pas à l'environnement");
        }
        if (!license.memberModules().containsAll(manifest.memberModules())
                || !license.simulatorModules().containsAll(manifest.simulatorModules())) {
            throw new IllegalArgumentException("La sélection contient un module non autorisé par la licence");
        }
    }

    public PrivateKey readPrivateKey(Path path) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readPem(path)));
    }

    public PublicKey readPublicKey(Path path) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(readPem(path)));
    }

    private TechnicalLicense normalize(TechnicalLicense license) {
        List<String> members = license.memberModules().stream().sorted(Comparator.naturalOrder()).toList();
        List<String> simulators = license.simulatorModules().stream().sorted(Comparator.naturalOrder()).toList();
        return new TechnicalLicense(license.licenseId(), license.clientCode(), license.clientName(),
                license.environmentCode(), license.issuedAt(), license.validFrom(), license.validUntil(),
                members, simulators, license.bundleVersion(), license.approvedBy(), license.localTest());
    }

    private static void writePem(Path path, String type, byte[] encoded) throws IOException {
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        String content = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        Files.writeString(path, content, StandardCharsets.US_ASCII);
    }

    private static byte[] readPem(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.US_ASCII)
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }
}
