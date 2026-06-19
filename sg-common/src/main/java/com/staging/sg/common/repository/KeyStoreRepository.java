package com.staging.sg.common.repository;

import com.staging.sg.common.entity.KeyStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeyStoreRepository extends JpaRepository<KeyStore, Long> {
    Optional<KeyStore> findByMemberGroupIdAndKeyTypeAndStatus(String memberGroupId, String keyType, String status);
    List<KeyStore> findByMemberGroupId(String memberGroupId);
    List<KeyStore> findByKeyTypeAndStatus(String keyType, String status);
}
