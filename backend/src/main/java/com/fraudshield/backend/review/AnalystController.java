package com.fraudshield.backend.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudshield.backend.workflow.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/analyst/alerts")
public class AnalystController {
 private final AlertReviewService service;private final CurrentActor actors;
 public AnalystController(AlertReviewService service,CurrentActor actors){this.service=service;this.actors=actors;}
 @GetMapping public Object list(Authentication a,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status,@RequestParam(required=false)String priority,@RequestParam(name="claimed_by",required=false)String claimed,@RequestParam(name="sla_breached",required=false)Boolean breached){return service.alerts(page,size,status,priority,"me".equals(claimed)?actors.require(a).getId():null,breached);}
 @GetMapping("/{id}")public Object detail(@PathVariable UUID id){return service.detail(id);}
 @GetMapping("/{id}/history")public Object history(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.history(id,page,size);}
 @PostMapping("/{id}/claim")public Object claim(Authentication a,@PathVariable UUID id){return service.claim(id,actors.require(a));}
 @PostMapping("/{id}/resolutions")public Object resolve(Authentication a,@PathVariable UUID id,@Valid @RequestBody Resolution request){return service.resolve(id,actors.require(a),request.resolution(),request.comment());}
 public record Resolution(@NotBlank String resolution,@NotBlank String comment){}
}
