package com.fraudshield.backend.config;

import com.fraudshield.backend.auth.ApplicationRole;
import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.auth.UserAccountRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "fraudshield.dev-users", name = "enabled", havingValue = "true")
@Order(10)
public class DevelopmentUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentUserSeeder.class);

    private final DevelopmentUserProperties properties;
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevelopmentUserSeeder(
            DevelopmentUserProperties properties,
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing(properties.cardholderUsername(), properties.cardholderPassword(), ApplicationRole.CARDHOLDER);
        createIfMissing(properties.analystUsername(), properties.analystPassword(), ApplicationRole.FRAUD_ANALYST);
        createIfMissing(properties.managerUsername(), properties.managerPassword(), ApplicationRole.FRAUD_RISK_MANAGER);
    }

    private void createIfMissing(String username, String rawPassword, ApplicationRole role) {
        requireConfigured(username, rawPassword, role);
        String normalizedUsername = UserAccount.normalizeUsername(username);
        if (repository.findByUsername(normalizedUsername).isPresent()) {
            return;
        }

        UserAccount account = UserAccount.create(
                normalizedUsername,
                passwordEncoder.encode(rawPassword),
                role,
                true,
                clock.instant());
        repository.save(account);
        log.info("Created development account '{}' with role {}", normalizedUsername, role);
    }

    private static void requireConfigured(String username, String password, ApplicationRole role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Development account credentials must be configured for role " + role);
        }
    }
}
