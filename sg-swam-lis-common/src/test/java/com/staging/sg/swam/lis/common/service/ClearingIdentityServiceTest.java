package com.staging.sg.swam.lis.common.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class ClearingIdentityServiceTest {
 @Test void functionalKeyDoesNotDependOnStanMissingFromLis(){
  var service=new ClearingIdentityService("0123456789abcdef");
  String a=service.functionalKey("TESTGRP01","620414260723","109201","654321",
    "20260723145135",1000,"504");
  String b=service.functionalKey("TESTGRP01","620414260723",null,"654321",
    "20260723145135",1000,"504");
  assertEquals(a,b);
 }
 @Test void canonicalDateIsStable(){
  assertEquals("20260723145135",ClearingIdentityService.canonicalTransactionDate(
    LocalDateTime.of(2026,7,23,14,51,35)));
 }
}
