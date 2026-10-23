package com.fraudshield.backend.auth;

import com.fraudshield.backend.auth.dto.IdentityResponse;
import com.fraudshield.backend.auth.dto.LoginRequest;
import com.fraudshield.backend.auth.dto.LoginResponse;
import com.fraudshield.backend.security.JwtService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String unavailableUserHash;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.unavailableUserHash = passwordEncoder.encode("fraudshield-unavailable-user-placeholder");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = UserAccount.normalizeUsername(request.username());
        Optional<UserAccount> candidate = userAccountRepository.findByUsername(username);
        String passwordHash = candidate.map(UserAccount::getPasswordHash).orElse(unavailableUserHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

        UserAccount user = candidate
                .filter(UserAccount::isEnabled)
                .filter(ignored -> passwordMatches)
                .orElseThrow(InvalidCredentialsException::new);

        String token = jwtService.issueToken(user);
        return new LoginResponse(token, "Bearer", jwtService.expirationSeconds(), IdentityResponse.from(user));
    }

    @Transactional(readOnly = true)
    public IdentityResponse currentIdentity(String username) {
        UserAccount user = userAccountRepository.findByUsername(UserAccount.normalizeUsername(username))
                .filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
        return IdentityResponse.from(user);
    }
}
