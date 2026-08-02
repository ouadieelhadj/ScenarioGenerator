package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.deployment.DeploymentAdminService;
import com.staging.sg.acquirer.deployment.DeploymentDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/deployments")
public class DeploymentAdminController {
    private final DeploymentAdminService service;

    public DeploymentAdminController(DeploymentAdminService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('DEPLOYMENT_VIEW')")
    public CatalogDto catalog() { return service.catalog(); }

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('DEPLOYMENT_VIEW')")
    public List<ClientDto> clients() { return service.clients(); }

    @PostMapping("/clients")
    @PreAuthorize("hasAuthority('DEPLOYMENT_PREPARE')")
    public ClientDto createClient(@RequestBody CreateClientRequest request, Authentication authentication) {
        return service.createClient(request, authentication.getName());
    }

    @GetMapping("/environments")
    @PreAuthorize("hasAuthority('DEPLOYMENT_VIEW')")
    public List<EnvironmentDto> environments(@RequestParam Long clientId) {
        return service.environments(clientId);
    }

    @PostMapping("/environments")
    @PreAuthorize("hasAuthority('DEPLOYMENT_PREPARE')")
    public EnvironmentDto createEnvironment(@RequestBody CreateEnvironmentRequest request,
                                            Authentication authentication) {
        return service.createEnvironment(request, authentication.getName());
    }

    @PostMapping("/environments/{id}/preflight")
    @PreAuthorize("hasAuthority('DEPLOYMENT_PREPARE')")
    public PreflightDto preflight(@PathVariable Long id, Authentication authentication) {
        return service.preflight(id, authentication.getName());
    }

    @GetMapping("/licenses")
    @PreAuthorize("hasAuthority('DEPLOYMENT_VIEW')")
    public List<LicenseDto> licenses(@RequestParam(required = false) Long environmentId) {
        return service.deploymentLicenses(environmentId);
    }

    @PostMapping("/licenses")
    @PreAuthorize("hasAuthority('DEPLOYMENT_PREPARE')")
    public LicenseDto createLicense(@RequestBody CreateLicenseRequest request, Authentication authentication) {
        return service.createLicense(request, authentication.getName());
    }

    @PostMapping("/licenses/{id}/approve")
    @PreAuthorize("hasAuthority('DEPLOYMENT_APPROVE')")
    public LicenseDto approveLicense(@PathVariable UUID id, Authentication authentication) {
        return service.approveLicense(id, authentication.getName());
    }

    @GetMapping("/executions")
    @PreAuthorize("hasAuthority('DEPLOYMENT_VIEW')")
    public List<ExecutionDto> executions(@RequestParam(required = false) Long environmentId) {
        return service.executions(environmentId);
    }

    @PostMapping("/executions")
    @PreAuthorize("hasAuthority('DEPLOYMENT_EXECUTE')")
    public ExecutionDto createExecution(@RequestBody CreateExecutionRequest request, Authentication authentication) {
        return service.createExecution(request, authentication.getName());
    }

    @PostMapping("/executions/{id}/approve")
    @PreAuthorize("hasAuthority('DEPLOYMENT_APPROVE')")
    public ExecutionDto approveExecution(@PathVariable UUID id, Authentication authentication) {
        return service.approveExecution(id, authentication.getName());
    }
}
