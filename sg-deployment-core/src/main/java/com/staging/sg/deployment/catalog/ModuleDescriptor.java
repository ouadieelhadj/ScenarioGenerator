package com.staging.sg.deployment.catalog;

import com.staging.sg.deployment.model.ModuleSide;

import java.util.List;

public record ModuleDescriptor(
        String code,
        String label,
        ModuleSide side,
        String artifactId,
        String mainClass,
        Integer defaultPort,
        List<String> requiredVariables
) {}
