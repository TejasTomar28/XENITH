package com.fraudshield.backend.review;

import com.fraudshield.backend.authorization.TransactionAuthorizationService;
import com.fraudshield.backend.authorization.dto.AuthorizationRequest;
import com.fraudshield.backend.authorization.dto.AuthorizationResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@RequestMapping("/api/v1/manager/demo/authorisations")
public class DemoAuthorizationController {
    private final TransactionAuthorizationService authorizations;

    public DemoAuthorizationController(TransactionAuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    @PostMapping
    public ResponseEntity<AuthorizationResponse> authorize(@Valid @RequestBody AuthorizationRequest request) {
        return ResponseEntity.ok(authorizations.authorize(request));
    }
}
