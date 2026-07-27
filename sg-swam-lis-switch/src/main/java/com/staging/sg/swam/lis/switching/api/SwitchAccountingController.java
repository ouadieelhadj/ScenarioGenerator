package com.staging.sg.swam.lis.switching.api;
import com.staging.sg.swam.lis.common.model.AccountingBatchResult;
import com.staging.sg.swam.lis.switching.service.SwitchAccountingService;
import org.springframework.web.bind.annotation.*;import java.time.LocalDate;
@RestController @RequestMapping("/api/clearing/accounting")
public class SwitchAccountingController{private final SwitchAccountingService service;
 public SwitchAccountingController(SwitchAccountingService s){service=s;}
 @PostMapping("/post")public AccountingBatchResult post(@RequestParam LocalDate businessDate){return service.post(businessDate);}}
