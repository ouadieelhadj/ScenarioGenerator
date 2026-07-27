package com.staging.sg.swam.lis.member.api;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class MemberHealthController {
 @GetMapping("/api/clearing/health") public Map<String,String> health(){
  return Map.of("status","UP","module","swam-lis-member");}
}
