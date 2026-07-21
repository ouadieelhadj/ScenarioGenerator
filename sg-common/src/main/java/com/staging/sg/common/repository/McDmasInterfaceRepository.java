package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasInterface;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McDmasInterfaceRepository extends JpaRepository<McDmasInterface, String> {

    Optional<McDmasInterface> findByBankCode(String bankCode);

    /**
     * Retrouve une banque par son Group Sign-on ID (DE2 des 0800).
     * C'est ainsi que le Mastercard identifie le membre qui se connecte :
     * il recoit 40260 et en deduit la banque 022905, donc TESTGRP01.
     */
    Optional<McDmasInterface> findByGroupSignonDe2(String groupSignonDe2);

    List<McDmasInterface> findByActiveTrue();
}
