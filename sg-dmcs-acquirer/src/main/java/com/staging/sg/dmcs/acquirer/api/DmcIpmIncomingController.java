package com.staging.sg.dmcs.acquirer.api;

import com.staging.sg.dmcs.acquirer.service.IpmReaderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dmcs/ipm")
public class DmcIpmIncomingController {
    private final IpmReaderService service;

    public DmcIpmIncomingController(IpmReaderService service) {
        this.service = service;
    }

    @PostMapping("/incoming")
    public ResponseEntity<Map<String, Object>> read(@RequestParam String path) throws Exception {
        var file = service.readFile(path);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", file.getId());
        result.put("fileId", file.getFileId());
        result.put("businessDate", file.getFileDate());
        result.put("transactions", file.getNbTransactions());
        result.put("amountChecksum", file.getTotalAmount());
        result.put("status", file.getStatus());
        return ResponseEntity.ok(result);
    }
}
