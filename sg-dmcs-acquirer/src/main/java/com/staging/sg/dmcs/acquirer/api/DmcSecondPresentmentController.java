package com.staging.sg.dmcs.acquirer.api;

import com.staging.sg.dmcs.acquirer.service.DmcSecondPresentmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dmcs/disputes/second-presentments")
public class DmcSecondPresentmentController {
    private final DmcSecondPresentmentService service;

    public DmcSecondPresentmentController(DmcSecondPresentmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> generate(
            @RequestBody DmcSecondPresentmentService.Command command) {
        try {
            return ResponseEntity.ok(service.generate(command));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse(exception.getMessage()));
        }
    }

    public record ErrorResponse(String error) {
    }
}
