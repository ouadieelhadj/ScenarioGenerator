package com.staging.sg.softpos.repository;

import com.staging.sg.softpos.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public final class SoftPosRepositories {
    private SoftPosRepositories() {}

    public interface DeviceRepository extends JpaRepository<SoftPosDevice, String> {
        Optional<SoftPosDevice> findByDeviceIdAndMemberId(String deviceId, String memberId);
        List<SoftPosDevice> findAllByMemberIdOrderByDeviceId(String memberId);
    }
    public interface ActivationRepository extends JpaRepository<SoftPosActivation, String> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select a from SoftPosActivation a where a.activationHash = :hash")
        Optional<SoftPosActivation> findLockedByHash(@Param("hash") String hash);
    }
    public interface RouteRepository extends JpaRepository<SoftPosPosServerRoute, String> {
        Optional<SoftPosPosServerRoute> findByMemberIdAndEnvironmentAndActiveTrue(String memberId, String environment);
        Optional<SoftPosPosServerRoute> findByMemberIdAndEnvironment(String memberId, String environment);
        List<SoftPosPosServerRoute> findAllByMemberIdOrderByEnvironment(String memberId);
    }
    public interface TransactionRepository extends JpaRepository<SoftPosTransaction, String> {
        Optional<SoftPosTransaction> findByMemberIdAndIdempotencyKey(String memberId, String idempotencyKey);
        Optional<SoftPosTransaction> findByMemberIdAndClientTransactionId(String memberId, String clientTransactionId);
        List<SoftPosTransaction> findTop100ByMemberIdOrderByUpdatedAtDesc(String memberId);
    }
}
