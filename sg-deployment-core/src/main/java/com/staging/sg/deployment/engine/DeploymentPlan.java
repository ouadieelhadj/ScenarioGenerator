package com.staging.sg.deployment.engine;

import java.util.List;

public record DeploymentPlan(String clientCode, String environmentCode, List<String> actions) {}
