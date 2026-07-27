package com.staging.sg.swam.lis.member.api;
import com.staging.sg.swam.lis.common.model.AccountingBatchResult;
import com.staging.sg.swam.lis.member.service.MemberAccountingService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/clearing/accounting")
public class MemberAccountingController{private final MemberAccountingService service;
 public MemberAccountingController(MemberAccountingService s){service=s;}
 @PostMapping("/post") public AccountingBatchResult post(@RequestParam LocalDate businessDate){return service.post(businessDate);}}
