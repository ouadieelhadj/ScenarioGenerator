package com.staging.sg.acquirer.api;

import com.staging.sg.common.dto.CreateUserRequest;
import com.staging.sg.common.dto.UserDto;
import com.staging.sg.acquirer.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // GET /api/admin/users
    @GetMapping
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    // GET /api/admin/users/{id}
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    // POST /api/admin/users
    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody CreateUserRequest req,
                                           Authentication auth) {
        return ResponseEntity.ok(userService.create(req, auth.getName()));
    }

    // PUT /api/admin/users/{id}
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable Long id,
                                           @RequestBody CreateUserRequest req) {
        return ResponseEntity.ok(userService.update(id, req));
    }

    // PUT /api/admin/users/{id}/toggle
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        userService.toggleActive(id);
        return ResponseEntity.ok(Map.of("message", "Status updated"));
    }

    // POST /api/admin/users/{userId}/tests/{testId}
    @PostMapping("/{userId}/tests/{testId}")
    public ResponseEntity<?> assignTest(@PathVariable Long userId,
                                         @PathVariable Long testId) {
        userService.assignTest(userId, testId);
        return ResponseEntity.ok(Map.of("message", "Test assigned"));
    }

    // DELETE /api/admin/users/{userId}/tests/{testId}
    @DeleteMapping("/{userId}/tests/{testId}")
    public ResponseEntity<?> unassignTest(@PathVariable Long userId,
                                           @PathVariable Long testId) {
        userService.unassignTest(userId, testId);
        return ResponseEntity.ok(Map.of("message", "Test unassigned"));
    }
}
