package com.staging.sg.threeds.member.api;

import com.staging.sg.common.threeds.*;
import com.staging.sg.threeds.member.service.MemberThreeDsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/3ds/member/v1")
public class ThreeDsMemberController {
    private final MemberThreeDsService service;

    public ThreeDsMemberController(MemberThreeDsService service) { this.service = service; }

    @PostMapping("/authentications")
    public ThreeDsStartResponse start(@RequestBody ThreeDsStartRequest request) {
        return service.start(request);
    }

    @GetMapping("/authentications/{id}")
    public ThreeDsStartResponse status(@PathVariable UUID id) { return service.status(id); }

    @PostMapping("/verifications")
    public ThreeDsVerificationResponse verify(
            @RequestBody ThreeDsVerificationRequest request) {
        return service.verify(request);
    }

    @PostMapping("/results")
    public ThreeDsRRes result(@RequestBody ThreeDsRReq request) {
        return service.receiveResult(request);
    }

    @PostMapping("/acs/areq")
    public ThreeDsARes areq(@RequestParam UUID dsTransId,
            @RequestBody ThreeDsAReq request) {
        return service.acsAuthenticate(request, dsTransId);
    }

    @PostMapping("/acs/creq")
    public ThreeDsCRes creq(@RequestBody ThreeDsCReq request) {
        return service.acsChallenge(request);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "module", "sg-3ds-member",
                "messageVersion", MemberThreeDsService.VERSION,
                "sandboxEvidence", true);
    }
}
