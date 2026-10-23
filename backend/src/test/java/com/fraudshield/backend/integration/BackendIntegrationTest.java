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
import com.fraudshield.backend.dataset.DatasetImportException;
import com.fraudshield.backend.dataset.EvaluationResetService;
import com.fraudshield.backend.dataset.ImportSummary;
import com.fraudshield.backend.dataset.OfficialDatasetImporter;
import com.fraudshield.backend.domain.model.TransactionChannel;
import com.fraudshield.backend.domain.model.TransactionStatus;
import com.fraudshield.backend.domain.repository.CardTransactionRepository;
import com.fraudshield.backend.domain.repository.CardRepository;
import com.fraudshield.backend.fraud.FraudEvaluationService;
import com.fraudshield.backend.fraud.MissingReferenceDataException;
import com.fraudshield.backend.fraud.MissingRuleConfigurationException;
import com.fraudshield.backend.fraud.ReasonCode;
import com.fraudshield.backend.fraud.RuleEvaluationResult;
import com.fraudshield.backend.fraud.TransactionEvaluationRequest;
import com.fraudshield.backend.security.GatewayApiKeyFilter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OfficialDatasetImporter datasetImporter;

    @Autowired
    EvaluationResetService resetService;

    @Autowired
    CardTransactionRepository transactionRepository;

    @Autowired
    CardRepository cardRepository;

    @Autowired
    FraudEvaluationService fraudEvaluationService;

    @BeforeEach
    void clearDomainData() {
        jdbc.execute("TRUNCATE TABLE audit_events, rule_configuration_versions, mcc_codes, country_risk CASCADE");
    }

    @Test
    void applicationContextStartsAndMigrationIsApplied() throws Exception {
        assertThat(flyway.info().applied()).hasSize(2);
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

    @Test
    void completeSchemaAndIndexesExist() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN
                ('customers','cards','merchants','mcc_codes','cities','country_risk','transactions','transaction_features',
                 'transaction_reason_codes','model_predictions','step_up_requests','fraud_alerts','manager_approval_proposals',
                 'notification_outbox','rule_configuration_versions','audit_events','data_import_executions')
                """, String.class)).hasSize(17);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname='idx_transactions_card_ts'", Integer.class)).isEqualTo(1);
    }

    @Test
    void importsSpecificationShapedDataIdempotentlyAndProtectsPan(@TempDir Path directory) throws Exception {
        writeOfficialFixture(directory, "C001", "T000000001", "2026-03-04T23:55:00+05:30");

        ImportSummary first = datasetImporter.importOfficialData(directory, "fixture-password-123");
        ImportSummary second = datasetImporter.importOfficialData(directory, "fixture-password-123");

        assertThat(first.inserted()).isEqualTo(8);
        assertThat(first.rejected()).isZero();
        assertThat(second.inserted()).isZero();
        assertThat(second.skipped()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT txn_ts FROM transactions WHERE txn_id='T000000001'", OffsetDateTime.class).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-03-04T23:55:00+05:30").toInstant());
        String hash = jdbc.queryForObject("SELECT pan_hash FROM cards WHERE card_id='CARD1'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("9999000011112222");
        assertThat(jdbc.queryForObject("SELECT last_four FROM cards WHERE card_id='CARD1'", String.class).trim()).isEqualTo("2222");
        assertThat(jdbc.queryForObject("SELECT configuration->>'velocity_burst_count' FROM rule_configuration_versions WHERE active", String.class))
                .isEqualTo("5");
    }

    @Test
    void importRejectsUnexpectedHeadersWithUsefulDiagnostic(@TempDir Path directory) throws Exception {
        writeOfficialFixture(directory, "C001", "T000000001", "2026-03-04T23:55:00+05:30");
        Files.writeString(directory.resolve("case_01_country_risk_V1.0.csv"), "bad_header,country_name,risk_level\nIN,India,LOW\n");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> datasetImporter.importOfficialData(directory, "fixture-password-123")))
                .isInstanceOf(DatasetImportException.class)
                .hasMessageContaining("unexpected headers");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM country_risk", Integer.class)).isZero();
    }

    @Test
    void importRejectsMissingForeignReferenceTransactionally(@TempDir Path directory) throws Exception {
        writeOfficialFixture(directory, "MISSING", "T000000001", "2026-03-04T23:55:00+05:30");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> datasetImporter.importOfficialData(directory, "fixture-password-123")))
                .isInstanceOf(DatasetImportException.class)
                .hasMessageContaining("references missing customers.customer_id");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM country_risk", Integer.class)).isZero();
    }

    @Test
    void historicalQueriesUseStrictPriorAndIstBoundaries() {
        seedReferenceData();
        OffsetDateTime current = OffsetDateTime.parse("2026-03-05T00:05:00+05:30");
        insertTransaction("T000000001", "2026-03-04T23:59:00+05:30", "100.00", "APPROVED", "POS", "MER1");
        insertTransaction("T000000002", "2026-03-05T00:01:00+05:30", "200.00", "APPROVED", "ATM", "MER1");
        insertTransaction("T000000003", "2026-03-05T00:02:00+05:30", "300.00", "BLOCKED", "ECOM", "MER2");
        insertTransaction("T000000004", "2026-03-05T00:03:00+05:30", "400.00", "STEP_UP_PENDING", "ECOM", "MER2");
        insertTransaction("T000000005", "2026-03-05T00:05:00+05:30", "500.00", "APPROVED", "POS", "MER1");

        assertThat(transactionRepository.countPriorTransactions("CARD1", current.minusMinutes(10), current)).isEqualTo(4);
        assertThat(transactionRepository.sumApprovedBeforeOnIstDay("CARD1", TransactionStatus.APPROVED,
                current.toLocalDate().atStartOfDay().atOffset(ZoneOffset.ofHoursMinutes(5, 30)), current)).isEqualByComparingTo("200.00");
        assertThat(transactionRepository.averageApprovedAmount("CARD1", TransactionStatus.APPROVED, current.minusDays(30), current).orElseThrow())
                .isEqualByComparingTo("150.00");
        assertThat(transactionRepository
                .findFirstByCardIdAndStatusAndChannelInAndTransactionTimestampGreaterThanEqualAndTransactionTimestampLessThanOrderByTransactionTimestampDescTransactionIdDesc(
                        "CARD1", TransactionStatus.APPROVED, Set.of(TransactionChannel.POS, TransactionChannel.ATM, TransactionChannel.CONTACTLESS), current.minusHours(6), current)
                .orElseThrow().getTransactionId()).isEqualTo("T000000002");
        assertThat(transactionRepository.existsPriorMerchantTransaction("CARD1", "MER2", current.minusDays(90), current)).isTrue();
        assertThat(transactionRepository.existsPriorMerchantTransaction("CARD1", "MER3", current.minusDays(90), current)).isFalse();
    }

    @Test
    void databaseConstraintsRejectDuplicateTransactionAndInvalidStatus() {
        seedReferenceData();
        insertTransaction("T000000001", "2026-03-05T10:00:00+05:30", "10.00", "APPROVED", "POS", "MER1");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> insertTransaction("T000000001", "2026-03-05T10:01:00+05:30", "10.00", "APPROVED", "POS", "MER1")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "UPDATE transactions SET status='UNKNOWN' WHERE txn_id='T000000001'")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditRowsAreAppendOnly() {
        jdbc.update("INSERT INTO audit_events(entity_type,entity_id,actor_id,actor_role,occurred_at,action,resettable) VALUES ('CARD','CARD1','system','SYSTEM',CURRENT_TIMESTAMP,'CREATED',false)");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("DELETE FROM audit_events")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void resetDeletesOnlyResettableActivityAndRestoresCardState() {
        seedReferenceData();
        insertTransaction("T000000001", "2026-03-04T10:00:00+05:30", "10.00", "APPROVED", "POS", "MER1");
        insertResettableTransaction("T000000002");
        jdbc.update("UPDATE cards SET card_status='BLOCKED' WHERE card_id='CARD1'");
        jdbc.update("INSERT INTO audit_events(entity_type,entity_id,actor_id,actor_role,occurred_at,action,resettable) VALUES ('TRANSACTION','T000000002','system','SYSTEM',CURRENT_TIMESTAMP,'DECIDED',true)");

        EvaluationResetService.ResetSummary result = resetService.reset();

        assertThat(result.deletedTransactions()).isEqualTo(1);
        assertThat(result.restoredCards()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id='T000000001'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id='T000000002'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id='CARD1'", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM country_risk", Integer.class)).isEqualTo(1);
    }

    @Test
    void workflowUniquenessConstraintsPreventDuplicateActiveRecords() {
        seedReferenceData();
        insertResettableTransaction("T000000001");
        UUID transactionId = jdbc.queryForObject("SELECT id FROM transactions WHERE txn_id='T000000001'", UUID.class);
        jdbc.update("INSERT INTO step_up_requests(id,transaction_id,customer_id,otp_hash,expires_at,status,created_at) VALUES (?,?, 'C001','hash',CURRENT_TIMESTAMP + interval '15 minutes','PENDING',CURRENT_TIMESTAMP)", UUID.randomUUID(), transactionId);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "INSERT INTO step_up_requests(id,transaction_id,customer_id,otp_hash,expires_at,status,created_at) VALUES (?,?, 'C001','hash',CURRENT_TIMESTAMP + interval '15 minutes','PENDING',CURRENT_TIMESTAMP)", UUID.randomUUID(), transactionId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO fraud_alerts(id,transaction_id,priority,status,sla_due_at,created_at) VALUES (?,?, 'HIGH','OPEN',CURRENT_TIMESTAMP + interval '30 minutes',CURRENT_TIMESTAMP)", UUID.randomUUID(), transactionId);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "INSERT INTO fraud_alerts(id,transaction_id,priority,status,sla_due_at,created_at) VALUES (?,?, 'HIGH','OPEN',CURRENT_TIMESTAMP + interval '30 minutes',CURRENT_TIMESTAMP)", UUID.randomUUID(), transactionId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void optimisticVersionPredicateRejectsStaleCardWrite() {
        seedReferenceData();
        assertThat(jdbc.update("UPDATE cards SET card_status='BLOCKED', version=version+1 WHERE card_id='CARD1' AND version=0")).isEqualTo(1);
        assertThat(jdbc.update("UPDATE cards SET card_status='ACTIVE', version=version+1 WHERE card_id='CARD1' AND version=0")).isZero();
        assertThat(jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id='CARD1'", String.class)).isEqualTo("BLOCKED");
    }

    @Test
    void cardholderOwnershipQueriesRemainIsolated() {
        seedReferenceData();
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users(id,username,password_hash,role,enabled,created_at) VALUES (?, 'owner_one', '$2a$12$01234567890123456789012345678901234567890123456789012','CARDHOLDER',true,CURRENT_TIMESTAMP)", firstUser);
        jdbc.update("INSERT INTO app_users(id,username,password_hash,role,enabled,created_at) VALUES (?, 'owner_two', '$2a$12$01234567890123456789012345678901234567890123456789012','CARDHOLDER',true,CURRENT_TIMESTAMP)", secondUser);
        jdbc.update("UPDATE customers SET user_id=? WHERE customer_id='C001'", firstUser);
        jdbc.update("INSERT INTO customers(customer_id,user_id,full_name,mobile,email,city,username) VALUES ('C002',?,'Other','000','other@example.test','Mumbai','owner_two')", secondUser);
        jdbc.update("INSERT INTO cards(card_id,customer_id,pan_hash,last_four,card_type,card_status,seed_card_status,issue_date,expiry_date,home_city,home_country,daily_limit,international_enabled) VALUES ('CARD2','C002','$2a$12$11234567890123456789012345678901234567890123456789012','3333','DEBIT','ACTIVE','ACTIVE','2025-01-01','2029-12-31','Mumbai','IN',100000,false)");
        insertTransaction("T000000001", "2026-03-05T10:00:00+05:30", "10.00", "APPROVED", "POS", "MER1");

        assertThat(cardRepository.findByCustomerUserIdOrderById(firstUser)).extracting("id").containsExactly("CARD1");
        assertThat(cardRepository.findByCustomerUserIdOrderById(secondUser)).extracting("id").containsExactly("CARD2");
        assertThat(transactionRepository.findByCardCustomerUserIdOrderByTransactionTimestampDesc(firstUser)).hasSize(1);
        assertThat(transactionRepository.findByCardCustomerUserIdOrderByTransactionTimestampDesc(secondUser)).isEmpty();
    }

    @Test
    void evaluationServiceCalculatesHistoryAndAppliesRulesEndToEnd() {
        seedReferenceData();
        insertActiveRuleConfiguration("config-v1", 5);
        insertTransaction("T000000001", "2026-03-04T13:05:00+05:30", "2400.00", "APPROVED", "POS", "MER1");

        RuleEvaluationResult result = fraudEvaluationService.evaluate(new TransactionEvaluationRequest(
                "T900000001", "CARD1", OffsetDateTime.parse("2026-03-04T14:05:00+05:30"),
                new BigDecimal("3500.00"), "INR", "MER1", "5411", TransactionChannel.POS, "Mumbai", "IN"));

        assertThat(result.decision().name()).isEqualTo("APPROVE");
        assertThat(result.reasonCodes()).isEmpty();
        assertThat(result.features().velocity10Minutes()).isZero();
        assertThat(result.features().approvedToday()).isEqualByComparingTo("2400.00");
        assertThat(result.features().averageAmount30Days()).isEqualByComparingTo("2400.00");
        assertThat(result.features().speedKilometresPerHour()).isZero();
        assertThat(result.features().newMerchant()).isFalse();
        assertThat(result.features().aiFeatures().velocity24Hours()).isEqualTo(1);
        assertThat(result.configurationVersion()).isEqualTo(padChecksum("config-v1"));
    }

    @Test
    void evaluationServiceCalculatesImpossibleTravelFromStoredCoordinates() {
        seedReferenceData();
        jdbc.update("INSERT INTO cities VALUES ('Delhi','IN',28.6139,77.2090,'METRO')");
        insertActiveRuleConfiguration("config-v2", 5);
        insertTransaction("T000000001", "2026-03-04T13:05:00+05:30", "2400.00", "APPROVED", "POS", "MER1");

        RuleEvaluationResult result = fraudEvaluationService.evaluate(new TransactionEvaluationRequest(
                "T900000001", "CARD1", OffsetDateTime.parse("2026-03-04T14:05:00+05:30"),
                new BigDecimal("4000.00"), "INR", "MER1", "5411", TransactionChannel.POS, "Delhi", "IN"));

        assertThat(result.reasonCodes()).containsExactly(ReasonCode.IMPOSSIBLE_TRAVEL);
        assertThat(result.features().speedKilometresPerHour()).isCloseTo(1148.09, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void activeEvaluationFailsClearlyWithoutPersistedConfiguration() {
        seedReferenceData();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> fraudEvaluationService.evaluate(new TransactionEvaluationRequest(
                "T900000001", "CARD1", OffsetDateTime.parse("2026-03-04T14:05:00+05:30"),
                new BigDecimal("3500.00"), "INR", "MER1", "5411", TransactionChannel.POS, "Mumbai", "IN"))))
                .isInstanceOf(MissingRuleConfigurationException.class)
                .hasMessageContaining("No active");
    }

    @Test
    void hardStopDoesNotRequireConfigurationOrHistoricalReferences() {
        seedReferenceData();
        jdbc.update("UPDATE cards SET card_status='BLOCKED' WHERE card_id='CARD1'");
        RuleEvaluationResult result = fraudEvaluationService.evaluate(new TransactionEvaluationRequest(
                "T900000001", "CARD1", OffsetDateTime.parse("2026-03-04T14:05:00+05:30"),
                new BigDecimal("3500.00"), "INR", "UNKNOWN", "9999", TransactionChannel.POS, "Unknown", "IN"));
        assertThat(result.reasonCodes()).containsExactly(ReasonCode.CARD_NOT_ACTIVE);
        assertThat(result.features()).isEqualTo(com.fraudshield.backend.fraud.CalculatedTransactionFeatures.empty());
    }

    @Test
    void missingGeographicReferenceFailsExplicitly() {
        seedReferenceData();
        insertActiveRuleConfiguration("config-v3", 5);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> fraudEvaluationService.evaluate(new TransactionEvaluationRequest(
                "T900000001", "CARD1", OffsetDateTime.parse("2026-03-04T14:05:00+05:30"),
                new BigDecimal("3500.00"), "INR", "MER1", "5411", TransactionChannel.POS, "Unknown", "IN"))))
                .isInstanceOf(MissingReferenceDataException.class)
                .hasMessageContaining("Unknown transaction city");
    }

    @Test
    void persistedConfigurationReplacementChangesTheNextDecision() {
        seedReferenceData();
        insertTransaction("T000000001", "2026-03-04T14:00:00+05:30", "1000.00", "APPROVED", "POS", "MER1");
        insertTransaction("T000000002", "2026-03-04T14:02:00+05:30", "1000.00", "APPROVED", "POS", "MER1");
        insertActiveRuleConfiguration("config-v1", 5);
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                "T900000001", "CARD1", OffsetDateTime.parse("2026-03-04T14:05:00+05:30"),
                new BigDecimal("1000.00"), "INR", "MER1", "5411", TransactionChannel.POS, "Mumbai", "IN");
        assertThat(fraudEvaluationService.evaluate(request).decision().name()).isEqualTo("APPROVE");

        jdbc.update("UPDATE rule_configuration_versions SET active=false WHERE active");
        insertActiveRuleConfiguration("config-v2", 2);

        RuleEvaluationResult changed = fraudEvaluationService.evaluate(request);
        assertThat(changed.reasonCodes()).containsExactly(ReasonCode.VELOCITY_BURST);
        assertThat(changed.configurationVersion()).isEqualTo(padChecksum("config-v2"));
    }

    @Test
    void historyWindowIncludesLowerBoundaryAndExcludesEqualTimestamp() {
        seedReferenceData();
        OffsetDateTime current = OffsetDateTime.parse("2026-03-04T14:05:00+05:30");
        insertTransaction("T000000001", "2026-03-04T13:55:00+05:30", "1000.00", "APPROVED", "POS", "MER1");
        insertTransaction("T000000002", "2026-03-04T14:05:00+05:30", "1000.00", "APPROVED", "POS", "MER1");
        assertThat(transactionRepository.countPriorTransactions("CARD1", current.minusMinutes(10), current)).isEqualTo(1);
    }

    private void seedReferenceData() {
        jdbc.update("INSERT INTO country_risk VALUES ('IN','India','LOW')");
        jdbc.update("INSERT INTO cities VALUES ('Mumbai','IN',19.0760,72.8777,'METRO')");
        jdbc.update("INSERT INTO mcc_codes VALUES ('5411','Grocery','RETAIL',false)");
        jdbc.update("INSERT INTO customers(customer_id,full_name,mobile,email,city,username) VALUES ('C001','Customer','000','c@example.test','Mumbai','fixture')");
        jdbc.update("INSERT INTO cards(card_id,customer_id,pan_hash,last_four,card_type,card_status,seed_card_status,issue_date,expiry_date,home_city,home_country,daily_limit,international_enabled) VALUES ('CARD1','C001','$2a$12$01234567890123456789012345678901234567890123456789012','2222','DEBIT','ACTIVE','ACTIVE','2025-01-01','2029-12-31','Mumbai','IN',100000,false)");
        jdbc.update("INSERT INTO merchants VALUES ('MER1','Merchant One','5411','Grocery','Mumbai','IN','LOW')");
        jdbc.update("INSERT INTO merchants VALUES ('MER2','Merchant Two','5411','Grocery','Mumbai','IN','LOW')");
    }

    private void insertTransaction(String txnId, String timestamp, String amount, String status, String channel, String merchant) {
        jdbc.update("""
                INSERT INTO transactions(id,txn_id,card_id,txn_ts,amount,currency,merchant_id,mcc,channel,txn_city,txn_country,status,
                    original_decision,original_status,transaction_source,resettable,processed_at)
                VALUES (?,?,?,?,?,'INR',?,'5411',?,'Mumbai','IN',?,?,?,'HISTORICAL',false,?)
                """, UUID.randomUUID(), txnId, "CARD1", OffsetDateTime.parse(timestamp), new BigDecimal(amount), merchant, channel,
                status, status.equals("STEP_UP_PENDING") ? "STEP_UP" : status.equals("APPROVED") ? "APPROVE" : "BLOCK",
                status, OffsetDateTime.parse(timestamp));
    }

    private void insertResettableTransaction(String txnId) {
        jdbc.update("""
                INSERT INTO transactions(id,txn_id,card_id,txn_ts,amount,currency,merchant_id,mcc,channel,txn_city,txn_country,status,
                    original_decision,original_status,transaction_source,resettable,processed_at)
                VALUES (?,?, 'CARD1','2026-03-05T10:00:00+05:30',10,'INR','MER1','5411','POS','Mumbai','IN','BLOCKED',
                    'BLOCK','BLOCKED','EVALUATION',true,'2026-03-05T10:00:00+05:30')
                """, UUID.randomUUID(), txnId);
    }

    private void writeOfficialFixture(Path directory, String cardCustomerId, String txnId, String txnTimestamp) throws Exception {
        Files.writeString(directory.resolve("case_01_country_risk_V1.0.csv"), "country_code,country_name,risk_level\nIN,India,LOW\n");
        Files.writeString(directory.resolve("case_01_cities_V1.0.csv"), "city,country,lat,lon,city_tier\nMumbai,IN,19.0760,72.8777,METRO\n");
        Files.writeString(directory.resolve("case_01_mcc_codes_V1.0.csv"), "mcc,mcc_description,mcc_category,high_risk\n5411,Grocery,RETAIL,N\n");
        Files.writeString(directory.resolve("case_01_customers_V1.0.csv"), "customer_id,full_name,mobile,email,city,username\nC001,Fixture Customer,0000000000,fixture@example.test,Mumbai,fixture_customer\n");
        Files.writeString(directory.resolve("case_01_cards_V1.0.csv"), "card_id,customer_id,card_number,card_type,card_status,issue_date,expiry_date,home_city,home_country,daily_limit,international_enabled\nCARD1," + cardCustomerId + ",9999000011112222,DEBIT,ACTIVE,2025-01-01,2029-12-31,Mumbai,IN,100000.00,N\n");
        Files.writeString(directory.resolve("case_01_merchants_V1.0.csv"), "merchant_id,merchant_name,mcc,mcc_description,city,country,merchant_risk\nMER1,Fixture Merchant,5411,Grocery,Mumbai,IN,LOW\n");
        Files.writeString(directory.resolve("case_01_rules_config_V1.0.json"), "{\"velocity_burst_count\":5,\"high_risk_mccs\":[\"4829\"]}\n");
        Files.writeString(directory.resolve("case_01_transactions_history_V1.0.csv"), "txn_id,card_id,txn_ts,amount,merchant_id,mcc,channel,txn_city,txn_country,status\n" + txnId + ",CARD1," + txnTimestamp + ",2400.00,MER1,5411,POS,Mumbai,IN,APPROVED\n");
    }

    private void insertActiveRuleConfiguration(String seed, int velocityBurstCount) {
        String checksum = padChecksum(seed);
        String json = """
                {"velocity_window_minutes":10,"velocity_24h_window_hours":24,"velocity_burst_count":%d,
                "high_velocity_min_count":3,"high_velocity_max_count":4,"amount_average_lookback_days":30,
                "amount_spike_multiplier":5,"amount_spike_minimum":10000,"amount_spike_no_history_minimum":50000,
                "impossible_travel_threshold_kmph":800,"impossible_travel_lookback_hours":6,
                "high_velocity_points":25,"amount_spike_points":30,"high_risk_geo_points":25,"high_risk_mcc_points":15,
                "odd_hour_points":15,"new_merchant_points":20,"high_risk_mccs":["4829","5967","6051","7995"],
                "odd_hour_start_inclusive":0,"odd_hour_end_inclusive":4,"odd_hour_minimum":20000,
                "new_merchant_lookback_days":90,"new_merchant_minimum":25000,"multiple_red_flags_count":3}
                """.formatted(velocityBurstCount);
        jdbc.update("INSERT INTO rule_configuration_versions(id,source_file,source_sha256,configuration,active,imported_at,imported_by) VALUES (?, 'fixture.json', ?, ?::jsonb, true, CURRENT_TIMESTAMP, 'test')",
                UUID.randomUUID(), checksum, json);
    }

    private String padChecksum(String seed) {
        return (seed + "0".repeat(64)).substring(0, 64);
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
