package com.fraudshield.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.auth.ApplicationRole;
import com.fraudshield.backend.auth.UserAccountRepository;
import com.fraudshield.backend.batch.BatchProcessingService;
import com.fraudshield.backend.authorization.ai.FraudModelClient;
import com.fraudshield.backend.authorization.ai.ModelEvaluation;
import com.fraudshield.backend.authorization.ai.ModelStatus;
import com.fraudshield.backend.authorization.ai.TopFactor;
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
import com.fraudshield.backend.workflow.OtpNotification;
import com.fraudshield.backend.workflow.StepUpVerificationService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    FraudModelClient modelClient;

    @MockBean
    OtpNotification otpNotification;

    @Autowired
    StepUpVerificationService stepUpVerificationService;

    @Autowired
    BatchProcessingService batchProcessingService;

    @BeforeEach
    void clearDomainData() {
        jdbc.execute("TRUNCATE TABLE audit_events, rule_configuration_versions, mcc_codes, country_risk CASCADE");
        reset(modelClient, otpNotification);
        when(modelClient.evaluate(any())).thenReturn(ModelEvaluation.unavailable());
    }

    @Test
    void applicationContextStartsAndMigrationIsApplied() throws Exception {
        assertThat(flyway.info().applied()).hasSize(5);
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
    void configuredFrontendOriginReceivesCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN,"http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,"POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,"content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,"http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,"true"));
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
    void validGatewayIdentityReachesAuthorizationValidation() throws Exception {
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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

    @Test
    void authorizationApprovesValidTransactionAndPersistsEvidence() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-approve", 5);

        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000001", "3500.00", "MER1", "5411", "POS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txn_id").value("T300000001"))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.rule_risk_score").value(0))
                .andExpect(jsonPath("$.model_status").value("MODEL_UNAVAILABLE"))
                .andExpect(jsonPath("$.alert_id").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id='T300000001'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_features", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM model_predictions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_type='TRANSACTION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void fraudBlockCreatesOneAlertAndPolicyBlockCreatesNone() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-block", 5);
        jdbc.update("UPDATE merchants SET merchant_risk='BLACKLISTED' WHERE merchant_id='MER1'");

        long visibilityStarted=System.nanoTime();
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000002", "3500.00", "MER1", "5411", "POS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reason_codes[0]").value("MERCHANT_BLACKLISTED"))
                .andExpect(jsonPath("$.alert_id").isNotEmpty())
                .andExpect(jsonPath("$.model_status").value("NOT_EVALUATED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fraud_alerts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT priority FROM fraud_alerts",String.class)).isEqualTo("MEDIUM");
        assertThat(jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM (sla_due_at-created_at))/60 FROM fraud_alerts",BigDecimal.class))
                .isEqualByComparingTo("240");
        String analyst=login("analyst",ANALYST_PASSWORD);
        mockMvc.perform(get("/api/v1/analyst/alerts").header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        long visibilityMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-visibilityStarted);
        System.out.printf("PERFORMANCE alert_visible_ms=%d%n",visibilityMillis);
        assertThat(visibilityMillis).isLessThan(5_000);
        verify(modelClient, never()).evaluate(any());

        jdbc.update("UPDATE merchants SET merchant_risk='LOW' WHERE merchant_id='MER1'");
        jdbc.update("UPDATE cards SET daily_limit=1000 WHERE card_id='CARD1'");
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000003", "3500.00", "MER1", "5411", "POS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reason_codes[0]").value("DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.alert_id").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fraud_alerts", Integer.class)).isEqualTo(1);
    }

    @Test
    void softRuleCreatesProtectedStepUpAndEmitsOtpAfterCommit() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-step-up", 5);
        AtomicReference<String> deliveredOtp = new AtomicReference<>();
        doAnswer(invocation -> { deliveredOtp.set(invocation.getArgument(2)); return null; }).when(otpNotification).send(any(), any(), any());

        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000004", "50000.00", "MER1", "5411", "ECOM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("STEP_UP"))
                .andExpect(jsonPath("$.status").value("STEP_UP_PENDING"))
                .andExpect(jsonPath("$.reason_codes[0]").value("AMOUNT_SPIKE"))
                .andExpect(jsonPath("$.alert_id").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM step_up_requests", Integer.class)).isEqualTo(1);
        assertThat(deliveredOtp.get()).matches("[0-9]{6}");
        String hash = jdbc.queryForObject("SELECT otp_hash FROM step_up_requests", String.class);
        assertThat(hash).doesNotContain(deliveredOtp.get());
    }

    @Test
    void cardholderCanConfirmOwnStepUpAndOtpCannotBeReplayed() throws Exception {
        seedReferenceData();
        jdbc.update("UPDATE customers SET user_id=(SELECT id FROM app_users WHERE username='cardholder') WHERE customer_id='C001'");
        insertActiveRuleConfiguration("step-up-confirm", 5);
        AtomicReference<String> otp = new AtomicReference<>();
        doAnswer(invocation -> { otp.set(invocation.getArgument(2)); return null; }).when(otpNotification).send(any(), any(), any());
        String authorizationBody=authorizationBody("T300000014", "50000.00", "MER1", "5411", "ECOM");
        String originalResponse=postAuthorization(authorizationBody).getResponse().getContentAsString();
        UUID stepUpId = jdbc.queryForObject("SELECT id FROM step_up_requests", UUID.class);
        String token = login("cardholder", CARDHOLDER_PASSWORD);

        mockMvc.perform(get("/api/v1/me/cards").header(HttpHeaders.AUTHORIZATION,"Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].masked_card_number").value("XXXXXXXXXXXX2222"));
        mockMvc.perform(get("/api/v1/me/transactions").header(HttpHeaders.AUTHORIZATION,"Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/me/transactions/T300000014").header(HttpHeaders.AUTHORIZATION,"Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STEP_UP_PENDING"));
        mockMvc.perform(get("/api/v1/me/step-ups").header(HttpHeaders.AUTHORIZATION,"Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].step_up_id").value(stepUpId.toString()));

        mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses", stepUpId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("response", "CONFIRM", "otp", otp.get()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE txn_id='T300000014'", String.class)).isEqualTo("APPROVED");

        mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses", stepUpId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("response", "CONFIRM", "otp", otp.get()))))
                .andExpect(status().isConflict());
        assertThat(postAuthorization(authorizationBody).getResponse().getContentAsString()).isEqualTo(originalResponse);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM step_up_requests",Integer.class)).isEqualTo(1);
        verify(modelClient).evaluate(any());
    }

    @Test
    void wrongOtpIsAuditedAndNotMeBlocksTransactionCardAndCreatesHighAlert() throws Exception {
        seedReferenceData();
        jdbc.update("UPDATE customers SET user_id=(SELECT id FROM app_users WHERE username='cardholder') WHERE customer_id='C001'");
        insertActiveRuleConfiguration("step-up-deny", 5);
        String authorizationBody=authorizationBody("T300000015", "50000.00", "MER1", "5411", "ECOM");
        String originalResponse=postAuthorization(authorizationBody).getResponse().getContentAsString();
        UUID stepUpId = jdbc.queryForObject("SELECT id FROM step_up_requests", UUID.class);
        String token = login("cardholder", CARDHOLDER_PASSWORD);

        mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses", stepUpId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"CONFIRM\",\"otp\":\"000000\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM step_up_requests WHERE id=?", Integer.class, stepUpId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_type='STEP_UP' AND action='OTP_FAILED'", Integer.class)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses", stepUpId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"NOT_ME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DENIED"));
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE txn_id='T300000015'", String.class)).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id='CARD1'", String.class)).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("SELECT priority FROM fraud_alerts", String.class)).isEqualTo("HIGH");
        assertThat(jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM (sla_due_at-created_at))/60 FROM fraud_alerts",BigDecimal.class))
                .isEqualByComparingTo("30");
        assertThat(jdbc.queryForList("SELECT reason_code FROM transaction_reason_codes ORDER BY sequence_no", String.class)).contains("STEP_UP_DENIED");
        assertThat(postAuthorization(authorizationBody).getResponse().getContentAsString()).isEqualTo(originalResponse);
        assertThat(objectMapper.readTree(originalResponse).get("alert_id").isNull()).isTrue();
        verify(modelClient).evaluate(any());
        String manager=login("manager",MANAGER_PASSWORD);
        mockMvc.perform(get("/api/v1/manager/audit-trails/transaction/T300000015")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer "+manager).param("size","100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].action",hasItems("AUTHORIZATION_DECIDED","STEP_UP_CREATED","STEP_UP_DENIED","CARD_BLOCKED","ALERT_CREATED")));
        jdbc.update("UPDATE fraud_alerts SET sla_due_at=CURRENT_TIMESTAMP-interval '1 second'");
        String analyst=login("analyst",ANALYST_PASSWORD);
        mockMvc.perform(get("/api/v1/analyst/alerts").header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst)
                        .param("sla_breached","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sla_breached").value(true));
    }

    @Test
    void expiryBlocksTransactionCreatesAlertAndLeavesCardActive() throws Exception {
        seedReferenceData();
        jdbc.update("UPDATE customers SET user_id=(SELECT id FROM app_users WHERE username='cardholder') WHERE customer_id='C001'");
        insertActiveRuleConfiguration("step-up-expiry", 5);
        String authorizationBody=authorizationBody("T300000016", "25000.00", "MER1", "5411", "ECOM");
        String originalResponse=postAuthorization(authorizationBody).getResponse().getContentAsString();
        UUID stepUpId = jdbc.queryForObject("SELECT id FROM step_up_requests", UUID.class);
        jdbc.update("UPDATE step_up_requests SET expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE id=?", stepUpId);

        String token=login("cardholder",CARDHOLDER_PASSWORD);
        mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses",stepUpId)
                        .header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"CONFIRM\",\"otp\":\"000000\"}"))
                .andExpect(status().isConflict());
        assertThat(stepUpVerificationService.expireOne(stepUpId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE txn_id='T300000016'", String.class)).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id='CARD1'", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fraud_alerts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT reason_code FROM transaction_reason_codes", String.class)).contains("STEP_UP_TIMEOUT");
        assertThat(postAuthorization(authorizationBody).getResponse().getContentAsString()).isEqualTo(originalResponse);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action='STEP_UP_EXPIRED'",Integer.class)).isEqualTo(1);
        verify(modelClient).evaluate(any());
    }

    @Test
    void analystAndManagerCompleteHighValueMakerCheckerLifecycle() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("review-workflow", 5);
        jdbc.update("UPDATE merchants SET merchant_risk='BLACKLISTED' WHERE merchant_id='MER1'");
        MvcResult authorization = postAuthorization(authorizationBody("T300000017", "150000.00", "MER1", "5411", "POS"));
        UUID alertId = UUID.fromString(objectMapper.readTree(authorization.getResponse().getContentAsString()).get("alert_id").asText());
        String analyst = login("analyst", ANALYST_PASSWORD);

        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/claim", alertId).header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/resolutions", alertId)
                        .header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"FALSE_POSITIVE\",\"comment\":\"Reviewed with the cardholder\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alert.status").value("PENDING_MANAGER_APPROVAL"));
        UUID proposalId=jdbc.queryForObject("SELECT id FROM manager_approval_proposals",UUID.class);
        String manager=login("manager",MANAGER_PASSWORD);
        mockMvc.perform(post("/api/v1/manager/false-positive-proposals/{id}/decisions",proposalId)
                        .header(HttpHeaders.AUTHORIZATION,"Bearer "+manager).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"comment\":\"Independent evidence review confirms fraud\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alert.status").value("CONFIRMED_FRAUD"));
        assertThat(jdbc.queryForObject("SELECT status FROM manager_approval_proposals WHERE id=?",String.class,proposalId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT card_status FROM cards WHERE card_id='CARD1'",String.class)).isEqualTo("BLOCKED");
        mockMvc.perform(get("/api/v1/manager/dashboard").header(HttpHeaders.AUTHORIZATION,"Bearer "+manager)
                        .param("from","2026-03-01T00:00:00+05:30").param("to","2026-11-01T00:00:00+05:30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transactions_by_decision.BLOCK").value(1))
                .andExpect(jsonPath("$.false_positive_rate").value(0.0));
    }

    @Test
    void exactMakerCheckerBoundaryFinalizesAndManagerCannotCheckOwnProposal() throws Exception {
        seedReferenceData();insertActiveRuleConfiguration("maker-checker",5);jdbc.update("UPDATE merchants SET merchant_risk='BLACKLISTED' WHERE merchant_id='MER1'");
        String analyst=login("analyst",ANALYST_PASSWORD);String manager=login("manager",MANAGER_PASSWORD);
        UUID lowAlert=UUID.fromString(objectMapper.readTree(postAuthorization(authorizationBody("T300000018","100000.00","MER1","5411","POS")).getResponse().getContentAsString()).get("alert_id").asText());
        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/claim",lowAlert).header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/resolutions",lowAlert).header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst).contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"FALSE_POSITIVE\",\"comment\":\"Reviewed exact threshold\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alert.status").value("FALSE_POSITIVE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM manager_approval_proposals",Integer.class)).isZero();

        jdbc.update("UPDATE cards SET daily_limit=200000 WHERE card_id='CARD1'");
        UUID highAlert=UUID.fromString(objectMapper.readTree(postAuthorization(authorizationBody("T300000019","100000.01","MER1","5411","POS")).getResponse().getContentAsString()).get("alert_id").asText());
        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/claim",highAlert).header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/resolutions",highAlert).header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst).contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"FALSE_POSITIVE\",\"comment\":\"Reviewed above threshold\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alert.status").value("PENDING_MANAGER_APPROVAL"));
        UUID proposal=jdbc.queryForObject("SELECT id FROM manager_approval_proposals",UUID.class);
        UUID managerId=userAccountRepository.findByUsername("manager").orElseThrow().getId();UUID analystId=userAccountRepository.findByUsername("analyst").orElseThrow().getId();
        jdbc.update("UPDATE manager_approval_proposals SET proposed_by=? WHERE id=?",managerId,proposal);
        mockMvc.perform(post("/api/v1/manager/false-positive-proposals/{id}/decisions",proposal).header(HttpHeaders.AUTHORIZATION,"Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"comment\":\"Independent review\"}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE manager_approval_proposals SET proposed_by=? WHERE id=?",analystId,proposal);
        mockMvc.perform(post("/api/v1/manager/false-positive-proposals/{id}/decisions",proposal).header(HttpHeaders.AUTHORIZATION,"Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"comment\":\"Independent review\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alert.status").value("FALSE_POSITIVE"));
    }

    @Test
    void managerConfigurationUpdateIsAuditedAndAffectsNextDecision() throws Exception {
        seedReferenceData();insertActiveRuleConfiguration("manager-config",5);insertTransaction("T000000097","2026-03-05T14:00:00+05:30","1000.00","APPROVED","POS","MER1");
        String analyst=login("analyst",ANALYST_PASSWORD);String manager=login("manager",MANAGER_PASSWORD);
        mockMvc.perform(put("/api/v1/manager/rule-configuration").header(HttpHeaders.AUTHORIZATION,"Bearer "+analyst).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        MvcResult current=mockMvc.perform(get("/api/v1/manager/rule-configuration").header(HttpHeaders.AUTHORIZATION,"Bearer "+manager)).andExpect(status().isOk()).andReturn();
        JsonNode currentJson=objectMapper.readTree(current.getResponse().getContentAsString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)currentJson.get("settings")).put("velocity_burst_count",1);
        String update=objectMapper.writeValueAsString(java.util.Map.of("expected_version",currentJson.get("version").asText(),"settings",currentJson.get("settings"),"comment","Lower threshold for controlled test"));
        mockMvc.perform(put("/api/v1/manager/rule-configuration").header(HttpHeaders.AUTHORIZATION,"Bearer "+manager).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());
        postAuthorization(authorizationBodyAt("T300000020","1000.00","2026-03-05T14:05:00+05:30"));
        assertThat(jdbc.queryForObject("SELECT rule_decision FROM transactions WHERE txn_id='T300000020'",String.class)).isEqualTo("BLOCK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_type='CONFIGURATION' AND action='CONFIGURATION_UPDATED'",Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentStepUpResponsesProduceOneFinalTransition() throws Exception {
        seedReferenceData();jdbc.update("UPDATE customers SET user_id=(SELECT id FROM app_users WHERE username='cardholder') WHERE customer_id='C001'");insertActiveRuleConfiguration("step-up-race",5);
        AtomicReference<String> otp=new AtomicReference<>();doAnswer(invocation->{otp.set(invocation.getArgument(2));return null;}).when(otpNotification).send(any(),any(),any());
        postAuthorization(authorizationBody("T300000021","50000.00","MER1","5411","ECOM"));UUID id=jdbc.queryForObject("SELECT id FROM step_up_requests",UUID.class);String token=login("cardholder",CARDHOLDER_PASSWORD);
        CountDownLatch start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try{var confirm=executor.submit(()->{start.await();return mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses",id).header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"CONFIRM\",\"otp\":\""+otp.get()+"\"}")).andReturn().getResponse().getStatus();});var deny=executor.submit(()->{start.await();return mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses",id).header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"NOT_ME\"}")).andReturn().getResponse().getStatus();});start.countDown();assertThat(Set.of(confirm.get(10,TimeUnit.SECONDS),deny.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}finally{executor.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_type='TRANSACTION' AND entity_id='T300000021' AND action IN ('STEP_UP_CONFIRMED','STEP_UP_DENIED')",Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentAnalystClaimsAndManagerDecisionsHaveOneWinner() throws Exception {
        seedReferenceData();insertActiveRuleConfiguration("human-races",5);jdbc.update("UPDATE merchants SET merchant_risk='BLACKLISTED' WHERE merchant_id='MER1'");
        UserAccount analystTwo=UserAccount.create("analyst_two",passwordEncoder.encode("analyst-two-password"),ApplicationRole.FRAUD_ANALYST,true,Instant.now());userAccountRepository.save(analystTwo);
        String analystOneToken=login("analyst",ANALYST_PASSWORD),analystTwoToken=login("analyst_two","analyst-two-password");
        UUID alert=UUID.fromString(objectMapper.readTree(postAuthorization(authorizationBody("T300000022","150000.00","MER1","5411","POS")).getResponse().getContentAsString()).get("alert_id").asText());
        CountDownLatch claimStart=new CountDownLatch(1);var claimPool=Executors.newFixedThreadPool(2);int firstStatus,secondStatus;
        try{var first=claimPool.submit(()->{claimStart.await();return mockMvc.perform(post("/api/v1/analyst/alerts/{id}/claim",alert).header(HttpHeaders.AUTHORIZATION,"Bearer "+analystOneToken)).andReturn().getResponse().getStatus();});var second=claimPool.submit(()->{claimStart.await();return mockMvc.perform(post("/api/v1/analyst/alerts/{id}/claim",alert).header(HttpHeaders.AUTHORIZATION,"Bearer "+analystTwoToken)).andReturn().getResponse().getStatus();});claimStart.countDown();firstStatus=first.get(10,TimeUnit.SECONDS);secondStatus=second.get(10,TimeUnit.SECONDS);assertThat(Set.of(firstStatus,secondStatus)).containsExactlyInAnyOrder(200,409);}finally{claimPool.shutdownNow();}
        UUID assigned=jdbc.queryForObject("SELECT assigned_analyst_id FROM fraud_alerts WHERE id=?",UUID.class,alert);String owner=assigned.equals(analystTwo.getId())?analystTwoToken:analystOneToken;String nonOwner=assigned.equals(analystTwo.getId())?analystOneToken:analystTwoToken;
        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/resolutions",alert).header(HttpHeaders.AUTHORIZATION,"Bearer "+nonOwner).contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"FALSE_POSITIVE\",\"comment\":\"Non owner cannot resolve\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/analyst/alerts/{id}/resolutions",alert).header(HttpHeaders.AUTHORIZATION,"Bearer "+owner).contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"FALSE_POSITIVE\",\"comment\":\"Concurrent claim winner review\"}")).andExpect(status().isOk());
        UUID proposal=jdbc.queryForObject("SELECT id FROM manager_approval_proposals",UUID.class);UserAccount managerTwo=UserAccount.create("manager_two",passwordEncoder.encode("manager-two-password"),ApplicationRole.FRAUD_RISK_MANAGER,true,Instant.now());userAccountRepository.save(managerTwo);String managerOne=login("manager",MANAGER_PASSWORD),managerTwoToken=login("manager_two","manager-two-password");
        CountDownLatch decisionStart=new CountDownLatch(1);var decisionPool=Executors.newFixedThreadPool(2);
        try{var approve=decisionPool.submit(()->{decisionStart.await();return mockMvc.perform(post("/api/v1/manager/false-positive-proposals/{id}/decisions",proposal).header(HttpHeaders.AUTHORIZATION,"Bearer "+managerOne).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"comment\":\"First independent review\"}")).andReturn().getResponse().getStatus();});var reject=decisionPool.submit(()->{decisionStart.await();return mockMvc.perform(post("/api/v1/manager/false-positive-proposals/{id}/decisions",proposal).header(HttpHeaders.AUTHORIZATION,"Bearer "+managerTwoToken).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\",\"comment\":\"Second independent review\"}")).andReturn().getResponse().getStatus();});decisionStart.countDown();assertThat(Set.of(approve.get(10,TimeUnit.SECONDS),reject.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}finally{decisionPool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_type='PROPOSAL' AND entity_id=? AND action IN ('PROPOSAL_APPROVED','PROPOSAL_REJECTED')",Integer.class,proposal.toString())).isEqualTo(1);
    }

    @Test
    void identicalRetryReturnsOriginalWithoutDuplicateEffectsOrModelCall() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-retry", 5);
        String body = authorizationBody("T300000005", "3500.00", "MER1", "5411", "POS");

        MvcResult first = mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class)).isEqualTo(1);
        verify(modelClient).evaluate(any());
    }

    @Test
    void tenAndFiftyConcurrentIdenticalRequestsCommitExactlyOnce() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-duplicate-race",5);

        assertConcurrentIdenticalAuthorizations(10,authorizationBody("T300000023","3500.00","MER1","5411","POS"));
        assertConcurrentIdenticalAuthorizations(50,authorizationBodyAt(
                "T300000024","3500.00","2026-03-05T14:06:00+05:30","MER1","5411","POS"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id IN ('T300000023','T300000024')",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_type='TRANSACTION' AND entity_id IN ('T300000023','T300000024') AND action='AUTHORIZATION_DECIDED'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM model_predictions WHERE transaction_id IN (SELECT id FROM transactions WHERE txn_id IN ('T300000023','T300000024'))",Integer.class)).isEqualTo(2);
        verify(modelClient,times(2)).evaluate(any());
    }

    @Test
    void confirmRacingWithExpiryProducesOneFinalOutcome() throws Exception {
        seedReferenceData();
        jdbc.update("UPDATE customers SET user_id=(SELECT id FROM app_users WHERE username='cardholder') WHERE customer_id='C001'");
        insertActiveRuleConfiguration("step-up-expiry-race",5);
        AtomicReference<String> otp=new AtomicReference<>();
        doAnswer(invocation->{otp.set(invocation.getArgument(2));return null;}).when(otpNotification).send(any(),any(),any());
        postAuthorization(authorizationBody("T300000025","50000.00","MER1","5411","ECOM"));
        UUID id=jdbc.queryForObject("SELECT id FROM step_up_requests",UUID.class);
        jdbc.update("UPDATE step_up_requests SET expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE id=?",id);
        String token=login("cardholder",CARDHOLDER_PASSWORD);
        CountDownLatch start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try{
            var confirm=executor.submit(()->{start.await();return mockMvc.perform(post("/api/v1/me/step-ups/{id}/responses",id)
                    .header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"response\":\"CONFIRM\",\"otp\":\""+otp.get()+"\"}")).andReturn().getResponse().getStatus();});
            var expiry=executor.submit(()->{start.await();return stepUpVerificationService.expireOne(id);});
            start.countDown();
            assertThat(confirm.get(10,TimeUnit.SECONDS)).isEqualTo(409);
            expiry.get(10,TimeUnit.SECONDS);
        }finally{executor.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT status FROM step_up_requests WHERE id=?",String.class,id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fraud_alerts",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action='STEP_UP_EXPIRED'",Integer.class)).isEqualTo(1);
        assertThat(stepUpVerificationService.expireOne(id)).isFalse();
    }

    @Test
    void changedPayloadForExistingTransactionReturnsConflict() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-conflict", 5);
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000006", "3500.00", "MER1", "5411", "POS")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000006", "3600.00", "MER1", "5411", "POS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT amount FROM transactions WHERE txn_id='T300000006'", BigDecimal.class))
                .isEqualByComparingTo("3500.00");
    }

    @Test
    void validatesInputAndUnknownReferences() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-validation", 5);
        String invalid = authorizationBody("BAD", "0", "MER1", "5411", "POS");
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors[*].field", hasItem("txn_id")))
                .andExpect(jsonPath("$.field_errors[*].field", hasItem("amount")));

        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000007", "3500.00", "MISSING", "5411", "POS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void aiEscalatesApproveOnceAndStepUpToBlock() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-ai", 5);
        List<TopFactor> factors = List.of(
                new TopFactor("amount", "Amount contributed to risk", 0.4),
                new TopFactor("channel", "Channel contributed to risk", 0.2),
                new TopFactor("new_merchant_flag", "Merchant familiarity contributed to risk", 0.1));
        when(modelClient.evaluate(any())).thenReturn(
                new ModelEvaluation(ModelStatus.AVAILABLE, new BigDecimal("0.82"), factors, "model-v1"),
                new ModelEvaluation(ModelStatus.AVAILABLE, new BigDecimal("0.93"), factors, "model-v1"));

        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000008", "3500.00", "MER1", "5411", "POS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("STEP_UP"))
                .andExpect(jsonPath("$.reason_codes[0]").value("HIGH_FRAUD_SCORE_MODEL"))
                .andExpect(jsonPath("$.fraud_score").value(0.82))
                .andExpect(jsonPath("$.top_factors.length()").value(3));

        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000009", "50000.00", "MER1", "5411", "ECOM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.reason_codes[0]").value("AMOUNT_SPIKE"))
                .andExpect(jsonPath("$.reason_codes[2]").value("HIGH_FRAUD_SCORE_MODEL"))
                .andExpect(jsonPath("$.alert_id").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT priority FROM fraud_alerts", String.class)).isEqualTo("HIGH");
    }

    @Test
    void modelFallbackUsesRulesOnlyDecisionAndPersistsUnavailability() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-fallback", 5);
        when(modelClient.evaluate(any())).thenReturn(ModelEvaluation.unavailable());

        String body=authorizationBody("T300000010", "3500.00", "MER1", "5411", "POS");
        MvcResult first=mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.fraud_score").doesNotExist())
                .andExpect(jsonPath("$.top_factors").isEmpty())
                .andExpect(jsonPath("$.model_status").value("MODEL_UNAVAILABLE")).andReturn();
        assertThat(postAuthorization(body).getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT model_status FROM model_predictions", String.class))
                .isEqualTo("MODEL_UNAVAILABLE");
        verify(modelClient).evaluate(any());
    }

    @Test
    void failedAuthorizationLeavesNoPartialRecord() throws Exception {
        seedReferenceData();
        mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizationBody("T300000011", "3500.00", "MER1", "5411", "POS")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class)).isZero();
    }

    @Test
    void retryAfterDatabaseFailureCommitsOnceWithoutPartialEffects() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("database-retry",5);
        String body=authorizationBody("T300000026","3500.00","MER1","5411","POS");
        jdbc.execute("CREATE OR REPLACE FUNCTION test_fail_feature_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated feature persistence failure'; END $$");
        jdbc.execute("CREATE TRIGGER test_fail_feature_insert BEFORE INSERT ON transaction_features FOR EACH ROW EXECUTE FUNCTION test_fail_feature_insert()");
        try{
            mockMvc.perform(post("/api/v1/authorisations").header(GatewayApiKeyFilter.API_KEY_HEADER,GATEWAY_KEY)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id='T300000026'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id='T300000026'",Integer.class)).isZero();
        }finally{
            jdbc.execute("DROP TRIGGER IF EXISTS test_fail_feature_insert ON transaction_features");
            jdbc.execute("DROP FUNCTION IF EXISTS test_fail_feature_insert()");
        }
        postAuthorization(body);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id='T300000026'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id='T300000026' AND action='AUTHORIZATION_DECIDED'",Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentSameCardAuthorizationsPreserveDailyLimit() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("auth-concurrency", 5);
        insertTransaction("T000000099", "2026-03-04T14:05:00+05:30", "20000.00", "APPROVED", "POS", "MER1");
        CountDownLatch firstInsideModel = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(modelClient.evaluate(any())).thenAnswer(invocation -> {
            firstInsideModel.countDown();
            assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
            return ModelEvaluation.unavailable();
        });

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> postAuthorization(
                    authorizationBodyAt("T300000012", "60000.00", "2026-03-05T14:05:00+05:30")));
            assertThat(firstInsideModel.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> postAuthorization(
                    authorizationBodyAt("T300000013", "60000.00", "2026-03-05T14:06:00+05:30")));
            releaseFirst.countDown();

            Set<String> decisions = Set.of(
                    objectMapper.readTree(first.get(10, TimeUnit.SECONDS).getResponse().getContentAsString()).get("decision").asText(),
                    objectMapper.readTree(second.get(10, TimeUnit.SECONDS).getResponse().getContentAsString()).get("decision").asText());
            assertThat(decisions).containsExactlyInAnyOrder("APPROVE", "BLOCK");
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id IN ('T300000012','T300000013')", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE txn_id IN ('T300000012','T300000013') AND original_decision='BLOCK'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void batchUsesDeterministicRulesOnlyHistoryAndWritesOfficialSchemas(@TempDir Path directory) throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("batch-config",5);
        insertTransaction("T000000098","2026-03-05T14:04:00+05:30","99999.00","APPROVED","POS","MER1");
        jdbc.update("UPDATE transactions SET transaction_source='LIVE' WHERE txn_id='T000000098'");
        jdbc.update("UPDATE cards SET card_status='BLOCKED' WHERE card_id='CARD1'");
        Path input=directory.resolve("case_01_batch_transactions_sample_V1.0.csv");
        Files.writeString(input,"""
                txn_id,card_id,txn_ts,amount,currency,merchant_id,mcc,channel,txn_city,txn_country,expected_decision,expected_reason_codes,expected_rule_risk_score,expected_velocity_10m,expected_avg_amount_30d,expected_speed_kmph
                T400000002,CARD1,2026-03-05T14:06:00+05:30,1000.00,INR,MER1,5411,POS,Mumbai,IN,APPROVE,,0,1,1000.00,0.00
                T400000001,CARD1,2026-03-05T14:05:00+05:30,1000.00,INR,MER1,5411,POS,Mumbai,IN,APPROVE,,0,0,,
                """);

        var result=batchProcessingService.process(input,directory.resolve("out"));

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.sampleMatched()).isEqualTo(2);
        assertThat(result.sampleMismatches()).isEmpty();
        assertThat(Files.readAllLines(result.decisionsOutput())).containsExactly(
                "txn_id,decision,reason_codes,rule_risk_score,velocity_10m,avg_amount_30d,speed_kmph",
                "T400000001,APPROVE,,0,0,,",
                "T400000002,APPROVE,,0,1,1000.00,0.00");
        assertThat(Files.readAllLines(result.alertsOutput())).containsExactly("txn_id,alert_priority,sla_due_at");
        assertThat(Files.readAllLines(result.fraudScoresOutput())).containsExactly(
                "txn_id,fraud_score,final_decision,top_factors",
                "T400000001,,APPROVE,",
                "T400000002,,APPROVE,");
        assertThat(jdbc.queryForList("SELECT transaction_source FROM transactions WHERE txn_id LIKE 'T4%' ORDER BY txn_id",String.class))
                .containsExactly("BATCH_SAMPLE","BATCH_SAMPLE");
    }

    @Test
    void syntheticThreeHundredRowBatchCompletesWithinOfficialBudget(@TempDir Path directory) throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("batch-performance",5);
        StringBuilder csv=new StringBuilder("txn_id,card_id,txn_ts,amount,currency,merchant_id,mcc,channel,txn_city,txn_country\n");
        OffsetDateTime start=OffsetDateTime.parse("2026-03-05T10:00:00+05:30");
        for(int index=0;index<300;index++)csv.append("T5").append("%08d".formatted(index)).append(",CARD1,")
                .append(start.plusSeconds(index)).append(",1.00,INR,MER1,5411,POS,Mumbai,IN\n");
        Path input=directory.resolve("case_01_batch_transactions_eval_V1.0.csv");Files.writeString(input,csv);

        var result=batchProcessingService.process(input,directory.resolve("out"));

        assertThat(result.processed()).isEqualTo(300);
        assertThat(result.durationMillis()).isLessThan(60_000);
        assertThat(Files.readAllLines(result.decisionsOutput())).hasSize(301);
        assertThat(Files.readAllLines(result.fraudScoresOutput())).hasSize(301);
    }

    @Test
    void controlledAuthorizationLatencyMeetsRulesAndOverallTargets() throws Exception {
        seedReferenceData();
        insertActiveRuleConfiguration("latency-check",5);
        List<Long> fullPathMillis=new ArrayList<>();
        OffsetDateTime start=OffsetDateTime.parse("2026-03-05T06:00:00+05:30");
        for(int index=0;index<30;index++){
            long began=System.nanoTime();
            postAuthorization(authorizationBodyAt("T6"+"%08d".formatted(index),"1.00",start.plusMinutes(index*11L).toString()));
            fullPathMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began));
        }
        long fullP95=percentile95(fullPathMillis);
        jdbc.update("UPDATE cards SET card_status='BLOCKED' WHERE card_id='CARD1'");
        List<Long> rulesOnlyMillis=new ArrayList<>();
        for(int index=0;index<50;index++){
            long began=System.nanoTime();
            postAuthorization(authorizationBodyAt("T7"+"%08d".formatted(index),"1.00",start.plusMinutes(index).toString()));
            rulesOnlyMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began));
        }
        long rulesP95=percentile95(rulesOnlyMillis);
        System.out.printf("PERFORMANCE controlled_full_path_p95_ms=%d rules_only_p95_ms=%d%n",fullP95,rulesP95);
        assertThat(fullP95).isLessThanOrEqualTo(300);
        assertThat(rulesP95).isLessThanOrEqualTo(150);
    }

    private void assertConcurrentIdenticalAuthorizations(int requestCount,String body) throws Exception {
        CountDownLatch ready=new CountDownLatch(requestCount);
        CountDownLatch start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(requestCount);
        List<java.util.concurrent.Future<MvcResult>> requests=new ArrayList<>();
        try{
            for(int index=0;index<requestCount;index++){
                requests.add(executor.submit(()->{
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/api/v1/authorisations")
                            .header(GatewayApiKeyFilter.API_KEY_HEADER,GATEWAY_KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
                }));
            }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> responses=new ArrayList<>();
            for(var request:requests){
                MvcResult result=request.get(30,TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(200);
                responses.add(result.getResponse().getContentAsString());
            }
            assertThat(responses).allMatch(responses.get(0)::equals);
        }finally{
            start.countDown();
            executor.shutdownNow();
        }
    }

    private long percentile95(List<Long> values){
        List<Long> sorted=new ArrayList<>(values);Collections.sort(sorted);
        return sorted.get((int)Math.ceil(sorted.size()*0.95)-1);
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

    private String authorizationBody(String txnId, String amount, String merchantId, String mcc, String channel)
            throws Exception {
        return authorizationBodyAt(txnId, amount, "2026-03-05T14:05:00+05:30", merchantId, mcc, channel);
    }

    private String authorizationBodyAt(String txnId, String amount, String timestamp) throws Exception {
        return authorizationBodyAt(txnId, amount, timestamp, "MER1", "5411", "POS");
    }

    private String authorizationBodyAt(
            String txnId, String amount, String timestamp, String merchantId, String mcc, String channel) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "txn_id", txnId,
                "card_id", "CARD1",
                "txn_ts", timestamp,
                "amount", amount,
                "currency", "INR",
                "merchant_id", merchantId,
                "mcc", mcc,
                "channel", channel,
                "txn_city", "Mumbai",
                "txn_country", "IN"));
    }

    private MvcResult postAuthorization(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/authorisations")
                        .header(GatewayApiKeyFilter.API_KEY_HEADER, GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
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
                "new_merchant_lookback_days":90,"new_merchant_minimum":25000,"multiple_red_flags_count":3,
                "alert_high_amount":50000,"alert_high_sla_minutes":30,"alert_medium_sla_minutes":240,
                "ai_step_up_threshold":0.70,"ai_block_threshold":0.90}
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
