package com.staging.sg.deployment.manifest;

import com.staging.sg.deployment.model.DatabaseType;
import com.staging.sg.deployment.model.ShellType;
import com.staging.sg.deployment.model.TargetOs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestLoaderTest {
    @TempDir Path temp;

    @Test
    void loadsTypedManifestWithoutResolvingSecretValues() throws Exception {
        Path manifest = temp.resolve("deployment-manifest.yml");
        Files.writeString(manifest, """
                schemaVersion: '1'
                clientCode: LOCAL_TEST_BANK
                clientName: Banque Test Locale
                environmentCode: LOCAL
                targetOs: WINDOWS
                shellType: POWERSHELL
                deploymentRoot: runtime/deployment/local
                javaExecutable: java
                database:
                  type: POSTGRESQL
                  host: localhost
                  port: 5432
                  database: scenariogenerator
                  schema: public
                  user: scenario_user
                  passwordSecretRef: secret://local/db-password
                memberModules: [CARD_ISSUING]
                simulatorModules: [MERCHANT_SITE_SIMULATOR]
                variables:
                  CARD_ISSUING_DB_PASSWORD: secret://local/card-db-password
                bundles:
                  members: bundles/scenario-members-bundle.jar
                  simulators: bundles/scenario-simulators-bundle.jar
                licenseFile: licenses/license.json.sig
                licensePublicKey: licenses/license-public.pem
                """);

        var loaded = new ManifestLoader().load(manifest);
        assertEquals(TargetOs.WINDOWS, loaded.targetOs());
        assertEquals(ShellType.POWERSHELL, loaded.shellType());
        assertEquals(DatabaseType.POSTGRESQL, loaded.database().type());
        assertEquals("secret://local/db-password", loaded.database().passwordSecretRef());
        assertTrue(loaded.deploymentRoot().startsWith(temp));
    }
}
