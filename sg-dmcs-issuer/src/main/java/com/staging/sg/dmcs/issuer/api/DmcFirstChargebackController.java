package com.staging.sg.dmcs.issuer.api;

import com.staging.sg.dmcs.issuer.service.DmcFirstChargebackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dmcs/disputes/first-chargebacks")
public class DmcFirstChargebackController {
    private final DmcFirstChargebackService service;

    public DmcFirstChargebackController(DmcFirstChargebackService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> generate(
            @RequestBody DmcFirstChargebackService.Command command) {
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
