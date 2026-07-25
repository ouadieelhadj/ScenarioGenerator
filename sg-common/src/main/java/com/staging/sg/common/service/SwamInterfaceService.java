package com.staging.sg.common.service;

import com.staging.sg.common.entity.SwamInterface;
import com.staging.sg.common.repository.SwamInterfaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "sg.network.code", havingValue = "SWAM")
public class SwamInterfaceService {

    private final SwamInterface config;

    public SwamInterfaceService(SwamInterfaceRepository repository,
                                @Value("${sg.interface:}") String interfaceId) {
        if (interfaceId == null || interfaceId.isBlank()) {
            throw new IllegalStateException(
                    "[SWAM-IF] --sg.interface est obligatoire pour un module SWAM");
        }
        config = repository.findById(interfaceId)
                .filter(i -> Boolean.TRUE.equals(i.getActive()))
                .orElseThrow(() -> new IllegalStateException(
                        "[SWAM-IF] interface absente ou inactive : " + interfaceId));
    }

    public SwamInterface get() {
        return config;
    }
}
