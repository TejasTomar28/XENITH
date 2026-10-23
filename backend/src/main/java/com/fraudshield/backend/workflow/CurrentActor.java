package com.fraudshield.backend.workflow;

import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.auth.UserAccountRepository;
import com.fraudshield.backend.common.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {
    private final UserAccountRepository users;
    public CurrentActor(UserAccountRepository users) { this.users = users; }
    public UserAccount require(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }
}
