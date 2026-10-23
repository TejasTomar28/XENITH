package com.fraudshield.backend.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "fraudshield-backend", clock.instant()));
    }

    record HealthResponse(String status, String service, @JsonProperty("checked_at") Instant checkedAt) {
    }
}
