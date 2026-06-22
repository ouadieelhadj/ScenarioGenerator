package com.staging.sg.acquirer.generation;

import com.staging.sg.common.entity.BinRange;
import com.staging.sg.common.entity.IsoFieldCatalog;
import com.staging.sg.common.repository.BinRangeRepository;
import com.staging.sg.common.repository.IsoFieldCatalogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final IsoFieldCatalogRepository fieldRepo;
    private final BinRangeRepository binRepo;

    public CatalogController(IsoFieldCatalogRepository fieldRepo, BinRangeRepository binRepo) {
        this.fieldRepo = fieldRepo;
        this.binRepo = binRepo;
    }

    @GetMapping("/fields")
    public List<IsoFieldCatalog> fields() {
        return fieldRepo.findByEnabledTrueOrderByDisplayOrderAsc();
    }

    @GetMapping("/bin-ranges")
    public List<BinRange> binRanges() {
        return binRepo.findByEnabledTrue();
    }
}
