package com.fraudshield.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.auth.UserAccountRepository;
import com.fraudshield.backend.security.GatewayApiKeyFilter;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class BackendIntegrationTest {

    private static final String CARDHOLDER_PASSWORD = "cardholder-test-password";
    private static final String ANALYST_PASSWORD = "analyst-test-password";
    private static final String MANAGER_PASSWORD = "manager-test-password";
    private static final String GATEWAY_KEY = "gateway-test-key-at-least-24-characters";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fraudshield_test")
            .withUsername("fraudshield_test")
            .withPassword("fraudshield_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("fraudshield.security.jwt.secret", () -> "integration-jwt-secret-that-is-at-least-32-characters");
        registry.add("fraudshield.security.jwt.issuer", () -> "fraudshield-integration-test");
        registry.add("fraudshield.security.gateway.api-key", () -> GATEWAY_KEY);
        registry.add("fraudshield.dev-users.enabled", () -> true);
        registry.add("fraudshield.dev-users.cardholder-password", () -> CARDHOLDER_PASSWORD);
        registry.add("fraudshield.dev-users.analyst-password", () -> ANALYST_PASSWORD);
        registry.add("fraudshield.dev-users.manager-password", () -> MANAGER_PASSWORD);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Flyway flyway;

    @Autowired
    DataSource dataSource;

    @Test
    void applicationContextStartsAndMigrationIsApplied() throws Exception {
        assertThat(flyway.info().applied()).hasSize(1);
        try (var connection = dataSource.getConnection();
                var result = connection.getMetaData().getTables(null, null, "app_users", new String[] {"TABLE"})) {
            assertThat(result.next()).isTrue();
        }
    }

    @Test
    void healthEndpointReturnsJson() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("fraudshield-backend"));
    }

    @Test
    void authenticatesAndReturnsCurrentIdentity() throws Exception {
        String token = login("cardholder", CARDHOLDER_PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cardholder"))
                .andExpect(jsonPath("$.roles", hasItem("CARDHOLDER")));
    }

    @Test
    void rejectsIncorrectCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("cardholder", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsUnauthorizedRole() throws Exception {
        String cardholderToken = login("cardholder", CARDHOLDER_PASSWORD);

        mockMvc.perform(get("/api/v1/analyst/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cardholderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void rejectsMissingGatewayKey() throws Exception {
        mockMvc.perform(post("/api/v1/authorisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_GATEWAY_API_KEY"));
    }

    @Test
    void rejectsInvalidGatewayKey() throws Exception {
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, "wrong-gateway-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_GATEWAY_API_KEY"));
    }

    @Test
    void jwtCannotSubstituteForGatewayAuthentication() throws Exception {
        String cardholderToken = login("cardholder", CARDHOLDER_PASSWORD);

        mockMvc.perform(post("/api/v1/authorisations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cardholderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_GATEWAY_API_KEY"));
    }

    @Test
    void validGatewayIdentityCanReachUnimplementedAuthorizationRoute() throws Exception {
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void gatewayKeyCannotAccessUserApi() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void developmentPasswordsAreStoredAsBcryptHashes() {
        UserAccount cardholder = userAccountRepository.findByUsername("cardholder").orElseThrow();

        assertThat(cardholder.getPasswordHash()).startsWith("$2");
        assertThat(cardholder.getPasswordHash()).isNotEqualTo(CARDHOLDER_PASSWORD);
        assertThat(passwordEncoder.matches(CARDHOLDER_PASSWORD, cardholder.getPasswordHash())).isTrue();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get("access_token").textValue();
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("username", username)
                .put("password", password));
    }
}
