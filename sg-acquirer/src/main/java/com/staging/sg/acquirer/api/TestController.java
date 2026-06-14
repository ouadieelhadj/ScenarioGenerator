package com.staging.sg.acquirer.api;

import com.staging.sg.common.dto.TestDto;
import com.staging.sg.acquirer.TestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    // GET /api/admin/tests — tous les tests (admin)
    @GetMapping("/admin/tests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TestDto>> findAll() {
        return ResponseEntity.ok(testService.findAll());
    }

    // GET /api/tests/my — tests assignés (exploitation)
    @GetMapping("/tests/my")
    public ResponseEntity<List<TestDto>> findMyTests(Authentication auth) {
        // TODO : récupérer userId depuis auth
        return ResponseEntity.ok(testService.findAll());
    }

    // GET /api/admin/tests/{id}
    @GetMapping("/admin/tests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(testService.findById(id));
    }

    // POST /api/admin/tests
    @PostMapping("/admin/tests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestDto> create(@RequestBody TestDto dto,
                                           Authentication auth) {
        return ResponseEntity.ok(testService.create(dto, auth.getName()));
    }

    // PUT /api/admin/tests/{id}
    @PutMapping("/admin/tests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestDto> update(@PathVariable Long id,
                                           @RequestBody TestDto dto) {
        return ResponseEntity.ok(testService.update(id, dto));
    }

    // DELETE /api/admin/tests/{id}
    @DeleteMapping("/admin/tests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        testService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Test deleted"));
    }
}
