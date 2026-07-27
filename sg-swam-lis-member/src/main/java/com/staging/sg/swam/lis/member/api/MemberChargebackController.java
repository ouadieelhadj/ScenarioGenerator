package com.staging.sg.swam.lis.member.api;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.member.service.MemberChargebackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/clearing/chargebacks")
public class MemberChargebackController {
    private final MemberChargebackService service;
    public MemberChargebackController(MemberChargebackService service){this.service=service;}
    @PostMapping public ResponseEntity<ChargebackResult> emit(@Valid @RequestBody ChargebackRequest request){
        return ResponseEntity.ok(service.emit(request));}
    @PostMapping("/{id}/representation")
    public ResponseEntity<ChargebackResult> represent(@PathVariable Long id,
            @Valid @RequestBody RepresentationRequest request){return ResponseEntity.ok(service.represent(id,request));}
}
