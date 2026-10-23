package com.fraudshield.backend.authorization;

import com.fraudshield.backend.authorization.ai.FraudModelClient;
import com.fraudshield.backend.authorization.ai.ModelEvaluation;
import com.fraudshield.backend.authorization.ai.ModelStatus;
import com.fraudshield.backend.authorization.dto.AuthorizationRequest;
import com.fraudshield.backend.authorization.dto.AuthorizationResponse;
import com.fraudshield.backend.domain.model.AlertPriority;
import com.fraudshield.backend.domain.model.TransactionDecision;
import com.fraudshield.backend.domain.model.TransactionStatus;
import com.fraudshield.backend.domain.repository.CardRepository;
import com.fraudshield.backend.domain.repository.CityRepository;
import com.fraudshield.backend.domain.repository.CountryRiskRepository;
import com.fraudshield.backend.domain.repository.MerchantCategoryCodeRepository;
import com.fraudshield.backend.domain.repository.MerchantRepository;
import com.fraudshield.backend.fraud.FraudEvaluationService;
import com.fraudshield.backend.fraud.MissingReferenceDataException;
import com.fraudshield.backend.fraud.PersistedRuleConfigurationProvider;
import com.fraudshield.backend.fraud.ReasonCode;
import com.fraudshield.backend.fraud.RuleConfiguration;
import com.fraudshield.backend.fraud.RuleEvaluationResult;
import com.fraudshield.backend.fraud.TransactionEvaluationRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TransactionAuthorizationProcessor {
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);
    private static final Set<ReasonCode> ALERT_REASONS = EnumSet.of(
            ReasonCode.MERCHANT_BLACKLISTED,
            ReasonCode.VELOCITY_BURST,
            ReasonCode.IMPOSSIBLE_TRAVEL,
            ReasonCode.MULTIPLE_RED_FLAGS,
            ReasonCode.HIGH_FRAUD_SCORE_MODEL);
    private static final Set<ReasonCode> HIGH_PRIORITY_REASONS = EnumSet.of(
            ReasonCode.IMPOSSIBLE_TRAVEL,
            ReasonCode.MULTIPLE_RED_FLAGS);

    private final CardRepository cards;
    private final MerchantRepository merchants;
    private final MerchantCategoryCodeRepository mccCodes;
    private final CityRepository cities;
    private final CountryRiskRepository countries;
    private final FraudEvaluationService fraudEvaluationService;
    private final PersistedRuleConfigurationProvider configurations;
    private final FraudModelClient modelClient;
    private final AuthorizationRecordStore records;
    private final StepUpWorkflowHandoff stepUpHandoff;
    private final Clock clock;

    TransactionAuthorizationProcessor(
            CardRepository cards,
            MerchantRepository merchants,
            MerchantCategoryCodeRepository mccCodes,
            CityRepository cities,
            CountryRiskRepository countries,
            FraudEvaluationService fraudEvaluationService,
            PersistedRuleConfigurationProvider configurations,
            FraudModelClient modelClient,
            AuthorizationRecordStore records,
            StepUpWorkflowHandoff stepUpHandoff,
            Clock clock) {
        this.cards = cards;
        this.merchants = merchants;
        this.mccCodes = mccCodes;
        this.cities = cities;
        this.countries = countries;
        this.fraudEvaluationService = fraudEvaluationService;
        this.configurations = configurations;
        this.modelClient = modelClient;
        this.records = records;
        this.stepUpHandoff = stepUpHandoff;
        this.clock = clock;
    }

    @Transactional
    public AuthorizationResponse process(AuthorizationRequest request) {
        validateTimestamp(request);
        cards.lockById(request.cardId())
                .orElseThrow(() -> new MissingReferenceDataException("Unknown card: " + request.cardId()));
        StoredAuthorization existing = records.find(request.transactionId()).orElse(null);
        if (existing != null) {
            if (!existing.matches(request)) {
                throw new com.fraudshield.backend.common.exception.ConflictException(
                        "Transaction ID is already associated with a different request");
            }
            return existing.response();
        }
        validateReferences(request);

        TransactionEvaluationRequest evaluationRequest = new TransactionEvaluationRequest(
                request.transactionId(), request.cardId(), request.transactionTimestamp(), request.amount(), request.currency(),
                request.merchantId(), request.mcc(), request.channel(), request.transactionCity(), request.transactionCountry());
        RuleEvaluationResult ruleResult = fraudEvaluationService.evaluate(evaluationRequest);
        RuleConfiguration configuration = ruleResult.features().aiFeatures() == null ? null : configurations.current();
        ModelEvaluation model = evaluateModel(ruleResult);

        TransactionDecision finalDecision = ruleResult.decision();
        List<String> reasons = new ArrayList<>(ruleResult.reasonCodes().stream().map(Enum::name).toList());
        if (model.status() == ModelStatus.AVAILABLE) {
            if (ruleResult.decision() == TransactionDecision.APPROVE
                    && model.fraudScore().compareTo(configuration.aiStepUpThreshold()) >= 0) {
                finalDecision = TransactionDecision.STEP_UP;
                reasons.add(ReasonCode.HIGH_FRAUD_SCORE_MODEL.name());
            } else if (ruleResult.decision() == TransactionDecision.STEP_UP
                    && model.fraudScore().compareTo(configuration.aiBlockThreshold()) >= 0) {
                finalDecision = TransactionDecision.BLOCK;
                reasons.add(ReasonCode.HIGH_FRAUD_SCORE_MODEL.name());
            }
        }

        TransactionStatus finalStatus = switch (finalDecision) {
            case APPROVE -> TransactionStatus.APPROVED;
            case STEP_UP -> TransactionStatus.STEP_UP_PENDING;
            case BLOCK -> TransactionStatus.BLOCKED;
        };
        OffsetDateTime processedAt = OffsetDateTime.now(clock).withOffsetSameInstant(IST);
        UUID transactionId = UUID.randomUUID();
        UUID alertId = shouldCreateAlert(finalDecision, reasons) ? UUID.randomUUID() : null;
        AlertPriority priority = alertId == null ? null : priority(request.amount(), reasons, model, configuration);
        OffsetDateTime alertDueAt = priority == null ? null : processedAt.plusMinutes(
                priority == AlertPriority.HIGH ? configuration.alertHighSlaMinutes() : configuration.alertMediumSlaMinutes());

        records.persist(new AuthorizationPersistenceCommand(
                transactionId, request, ruleResult, finalDecision, finalStatus, reasons, model, alertId,
                priority == null ? null : priority.name(), alertDueAt, processedAt));
        if (finalStatus == TransactionStatus.STEP_UP_PENDING) {
            stepUpHandoff.transactionPending(transactionId, request.transactionId());
        }
        return records.find(request.transactionId()).orElseThrow().response();
    }

    private void validateTimestamp(AuthorizationRequest request) {
        if (!request.transactionTimestamp().getOffset().equals(IST)) {
            throw new SemanticValidationException("txn_ts", "Must use the +05:30 offset");
        }
    }

    private void validateReferences(AuthorizationRequest request) {
        if (!merchants.existsById(request.merchantId())) {
            throw new MissingReferenceDataException("Unknown merchant: " + request.merchantId());
        }
        if (!mccCodes.existsById(request.mcc())) {
            throw new MissingReferenceDataException("Unknown MCC: " + request.mcc());
        }
        var city = cities.findById(request.transactionCity())
                .orElseThrow(() -> new MissingReferenceDataException("Unknown transaction city: " + request.transactionCity()));
        if (!city.getCountry().getCountryCode().equals(request.transactionCountry())) {
            throw new MissingReferenceDataException("Transaction city and country do not match");
        }
        if (!countries.existsById(request.transactionCountry())) {
            throw new MissingReferenceDataException("Unknown country: " + request.transactionCountry());
        }
    }

    private ModelEvaluation evaluateModel(RuleEvaluationResult ruleResult) {
        if (ruleResult.decision() == TransactionDecision.BLOCK || ruleResult.features().aiFeatures() == null) {
            return ModelEvaluation.notEvaluated();
        }
        return modelClient.evaluate(ruleResult.features().aiFeatures());
    }

    private boolean shouldCreateAlert(TransactionDecision decision, List<String> reasons) {
        return decision == TransactionDecision.BLOCK
                && reasons.stream().map(ReasonCode::valueOf).anyMatch(ALERT_REASONS::contains);
    }

    private AlertPriority priority(
            BigDecimal amount,
            List<String> reasons,
            ModelEvaluation model,
            RuleConfiguration configuration) {
        boolean highReason = reasons.stream().map(ReasonCode::valueOf).anyMatch(HIGH_PRIORITY_REASONS::contains);
        boolean highModel = model.fraudScore() != null
                && model.fraudScore().compareTo(configuration.aiBlockThreshold()) >= 0;
        return amount.compareTo(configuration.alertHighAmount()) >= 0 || highReason || highModel
                ? AlertPriority.HIGH : AlertPriority.MEDIUM;
    }
}
