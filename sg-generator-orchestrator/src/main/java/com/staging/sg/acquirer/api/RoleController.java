package com.staging.sg.acquirer.api;

import com.staging.sg.common.entity.Permission;
import com.staging.sg.common.entity.Role;
import com.staging.sg.common.repository.PermissionRepository;
import com.staging.sg.common.repository.RoleRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class RoleController {

    private final RoleRepository roles;
    private final PermissionRepository permissions;

    public RoleController(RoleRepository roles, PermissionRepository permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    @GetMapping
    public List<RoleSummary> findAll() {
        return roles.findAll().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(RoleController::toSummary)
                .toList();
    }

    @GetMapping("/permissions")
    public List<PermissionSummary> permissions() {
        return permissions.findAll().stream()
                .sorted(Comparator.comparing(Permission::getCategory)
                        .thenComparing(Permission::getCode))
                .map(RoleController::toSummary)
                .toList();
    }

    private static RoleSummary toSummary(Role role) {
        List<PermissionSummary> rolePermissions = role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getCategory)
                        .thenComparing(Permission::getCode))
                .map(RoleController::toSummary)
                .toList();
        return new RoleSummary(role.getId(), role.getCode(), role.getLabel(),
                role.getDescription(), rolePermissions);
    }

    private static PermissionSummary toSummary(Permission permission) {
        return new PermissionSummary(permission.getId(), permission.getCode(),
                permission.getLabel(), permission.getCategory());
    }

    public record RoleSummary(Long id, String code, String label, String description,
                              List<PermissionSummary> permissions) {}

    public record PermissionSummary(Long id, String code, String label, String category) {}
}
