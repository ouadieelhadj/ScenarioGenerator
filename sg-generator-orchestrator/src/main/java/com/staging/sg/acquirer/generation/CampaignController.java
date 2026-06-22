package com.staging.sg.acquirer.generation;

import com.staging.sg.common.entity.Campaign;
import com.staging.sg.common.entity.GeneratedTransaction;
import com.staging.sg.common.repository.CampaignRepository;
import com.staging.sg.common.repository.GeneratedTransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignRepository campaignRepo;
    private final GeneratedTransactionRepository txRepo;
    private final GeneratorService generator;
    private final ExportService exportService;

    public CampaignController(CampaignRepository campaignRepo,
                             GeneratedTransactionRepository txRepo,
                             GeneratorService generator,
                             ExportService exportService) {
        this.campaignRepo = campaignRepo;
        this.txRepo = txRepo;
        this.generator = generator;
        this.exportService = exportService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Campaign c) {
        c.setId(null);
        c.setStatus("DRAFT");
        Campaign saved = campaignRepo.save(c);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public List<Campaign> list() {
        return campaignRepo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return campaignRepo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<?> generate(@PathVariable Long id) {
        try {
            int count = generator.generate(id);
            return ResponseEntity.ok(Map.of("campaignId", id, "generated", count, "status", "GENERATED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/{id}/transactions")
    public List<GeneratedTransaction> transactions(@PathVariable Long id) {
        return txRepo.findByCampaignId(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<?> export(@PathVariable Long id,
                                    @RequestParam(defaultValue = "json") String format) {
        try {
            String path = exportService.export(id, format);
            return ResponseEntity.ok(Map.of(
                    "campaignId", id,
                    "format", format,
                    "file", path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
