package com.staging.sg.common.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceOwnershipConfigurationTest {

    @Test
    void swamIssuerContainsOnlyItsSharedEntities() {
        List<String> names = new SwamIssuerPersistenceConfiguration()
                .swamIssuerManagedTypes().getManagedClassNames();

        assertTrue(names.stream().anyMatch(name -> name.endsWith(".SwamIssTransaction")));
        assertTrue(names.stream().anyMatch(name -> name.endsWith(".SwamIssuerCard")));
        assertTrue(names.stream().anyMatch(name -> name.endsWith(".SwamInterface")));
        assertFalse(names.stream().anyMatch(name -> name.endsWith(".SwamAcquirerCard")));
        assertFalse(names.stream().anyMatch(name -> name.contains(".McDmas")));
        assertFalse(names.stream().anyMatch(name -> name.contains(".McSms")));
        assertFalse(names.stream().anyMatch(name -> name.endsWith(".SwamAcqTransaction")));
    }

    @Test
    void swamAcquirerDoesNotContainIssuerOrMastercardEntities() {
        List<String> names = new SwamAcquirerPersistenceConfiguration()
                .swamAcquirerManagedTypes().getManagedClassNames();

        assertTrue(names.stream().anyMatch(name -> name.endsWith(".SwamAcqTransaction")));
        assertTrue(names.stream().anyMatch(name -> name.endsWith(".SwamAcquirerCard")));
        assertFalse(names.stream().anyMatch(name -> name.endsWith(".SwamIssTransaction")));
        assertFalse(names.stream().anyMatch(name -> name.endsWith(".SwamIssuerCard")));
        assertFalse(names.stream().anyMatch(name -> name.contains(".McDmas")));
    }

    @Test
    void lisSidesRemainSeparated() {
        PersistenceManagedTypes member = new SwamLisMemberPersistenceConfiguration()
                .swamLisMemberManagedTypes();
        PersistenceManagedTypes switching = new SwamLisSwitchPersistenceConfiguration()
                .swamLisSwitchManagedTypes();

        assertTrue(member.getManagedClassNames().stream()
                .allMatch(name -> !name.contains(".switching.")));
        assertTrue(switching.getManagedClassNames().stream()
                .allMatch(name -> !name.contains(".member.")));
    }

    @Test
    void mastercardDmasSidesRemainSeparated() {
        List<String> member = new McDmasMemberPersistenceConfiguration()
                .mcDmasMemberManagedTypes().getManagedClassNames();
        List<String> mastercard = new McDmasMastercardPersistenceConfiguration()
                .mcDmasMastercardManagedTypes().getManagedClassNames();

        assertFalse(member.stream().anyMatch(name -> name.endsWith(".McDmasMastercardKey")));
        assertFalse(mastercard.stream().anyMatch(name -> name.endsWith(".McDmasMemberKey")));
        assertFalse(member.stream().anyMatch(name -> name.startsWith("com.staging.sg.common.entity.Swam")));
        assertFalse(mastercard.stream().anyMatch(name -> name.startsWith("com.staging.sg.common.entity.Swam")));
    }
}
