package com.staging.sg.swam.lis.switching.api;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.switching.service.SwitchChargebackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/clearing/chargebacks")
public class SwitchChargebackController {
    private final SwitchChargebackService service;
    public SwitchChargebackController(SwitchChargebackService service){this.service=service;}
    @PostMapping public ResponseEntity<ChargebackResult> emit(@Valid @RequestBody ChargebackRequest request){
        return ResponseEntity.ok(service.emit(request));}
    @PostMapping("/{id}/representation")
    public ResponseEntity<ChargebackResult> represent(@PathVariable Long id,@Valid @RequestBody RepresentationRequest request){
        return ResponseEntity.ok(service.represent(id,request));}
}
