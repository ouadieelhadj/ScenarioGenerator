package com.staging.sg.common.repository;

import com.staging.sg.common.entity.ModuleUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModuleUserRepository extends JpaRepository<ModuleUser, Long> {
    Optional<ModuleUser> findByLogin(String login);
}
