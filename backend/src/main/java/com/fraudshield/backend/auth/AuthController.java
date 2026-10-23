package com.fraudshield.backend.auth;

import com.fraudshield.backend.auth.dto.IdentityResponse;
import com.fraudshield.backend.auth.dto.LoginRequest;
import com.fraudshield.backend.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @GetMapping("/me")
    ResponseEntity<IdentityResponse> me(Principal principal) {
        return ResponseEntity.ok(authenticationService.currentIdentity(principal.getName()));
    }
}
