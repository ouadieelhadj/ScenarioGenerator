package com.staging.sg.switchlab.bff.api;

import com.staging.sg.switchlab.bff.config.SwitchLabCorrelationFilter;
import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import com.staging.sg.switchlab.bff.service.SwitchLabTestCenterService;
import com.staging.sg.switchlab.bff.service.SwitchLabReportExportService;
import com.staging.sg.switchlab.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switchlab/v1/test-center")
public class SwitchLabTestCenterController {
    private final SwitchLabGatewayService authentication;
    private final SwitchLabTestCenterService service;
    private final SwitchLabReportExportService exports;

    public SwitchLabTestCenterController(SwitchLabGatewayService authentication, SwitchLabTestCenterService service,
                                         SwitchLabReportExportService exports) {
        this.authentication = authentication;
        this.service = service;
        this.exports = exports;
    }

    @GetMapping("/catalog")
    public List<SwitchLabTestCatalogItem> catalog(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) { requireAuthorized(authorization); return service.catalog(); }
    @GetMapping("/profiles")
    public List<SwitchLabProfileCapability> profiles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) { requireAuthorized(authorization); return service.profiles(); }
    @GetMapping("/campaigns")
    public List<SwitchLabCampaign> campaigns(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) { requireAuthorized(authorization); return service.campaigns(); }
    @PostMapping("/campaigns")
    public SwitchLabCampaign create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @RequestBody SwitchLabCampaignRequest request) { requireAuthorized(authorization); return service.create(request); }
    @PostMapping("/campaigns/{id}/run")
    public SwitchLabCampaignReport run(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                       @PathVariable String id, @RequestParam String environmentId,
                                       HttpServletRequest request) {
        requireAuthorized(authorization);
        return service.run(id, environmentId, correlation(request));
    }
    @GetMapping("/reports")
    public List<SwitchLabCampaignReport> reports(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) { requireAuthorized(authorization); return service.reports(); }
    @GetMapping("/reports/{id}/export")
    public ResponseEntity<byte[]> export(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @PathVariable String id, @RequestParam String format) {
        requireAuthorized(authorization);
        SwitchLabCampaignReport report = service.report(id);
        boolean pdf = "PDF".equalsIgnoreCase(format);
        boolean xlsx = "XLSX".equalsIgnoreCase(format);
        if (!pdf && !xlsx) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format must be PDF or XLSX");
        byte[] body = pdf ? exports.pdf(report) : exports.xlsx(report);
        String extension = pdf ? "pdf" : "xlsx";
        MediaType type = pdf ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"switchlab-report-" + id + "." + extension + "\"")
                .body(body);
    }
    @GetMapping("/evidence")
    public List<SwitchLabEvidence> evidence(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) { requireAuthorized(authorization); return service.evidence(); }
    @PostMapping("/evidence")
    public SwitchLabEvidence importEvidence(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                            @RequestBody SwitchLabEvidenceRequest request,
                                            HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        return service.importEvidence(request, correlation(servletRequest));
    }
    @PostMapping("/certification/analyze")
    public SwitchLabEvidence analyzeCertificationManifest(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody Map<String, Object> manifest, HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        return service.analyzeCertificationManifest(manifest, correlation(servletRequest));
    }

    private String correlation(HttpServletRequest request) { return String.valueOf(request.getAttribute(SwitchLabCorrelationFilter.HEADER)); }
    private void requireAuthorized(String authorization) { if (!authentication.authorized(authorization)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SwitchLab session"); }
}
