package com.fraudshield.backend.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fraudshield.backend.workflow.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController @RequestMapping("/api/v1/manager")
public class ManagerController {
 private final ManagerService service;private final CurrentActor actors;public ManagerController(ManagerService service,CurrentActor actors){this.service=service;this.actors=actors;}
 @GetMapping("/dashboard")public Object dashboard(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)OffsetDateTime from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)OffsetDateTime to){return service.dashboard(from,to);}
 @GetMapping("/false-positive-proposals")public Object proposals(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status){return service.proposals(page,size,status);}
 @GetMapping("/false-positive-proposals/{id}")public Object proposal(@PathVariable UUID id){return service.proposalDetail(id);}
 @PostMapping("/false-positive-proposals/{id}/decisions")public Object decide(Authentication a,@PathVariable UUID id,@Valid@RequestBody Decision r){return service.decide(id,actors.require(a),r.decision(),r.comment());}
 @GetMapping("/rule-configuration")public Object configuration(){return service.configuration();}
 @PutMapping("/rule-configuration")public Object configuration(Authentication a,@Valid@RequestBody ConfigurationUpdate r){return service.updateConfiguration(actors.require(a),r.expectedVersion(),r.settings(),r.comment());}
 @GetMapping("/audit-trails/{type}/{id}")public Object audits(@PathVariable String type,@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.audits(type,id,page,size);}
 @GetMapping(value="/audit-trails/{type}/{id}/export",produces="text/csv")public ResponseEntity<byte[]> auditExport(@PathVariable String type,@PathVariable String id){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=fraudshield-audit.csv").body(service.auditCsv(type,id));}
 public record Decision(@NotBlank String decision,@NotBlank String comment){}
 public record ConfigurationUpdate(@JsonProperty("expected_version")@NotBlank String expectedVersion,@NotNull JsonNode settings,@NotBlank String comment){}
}
