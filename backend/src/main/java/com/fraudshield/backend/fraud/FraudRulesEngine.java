package com.fraudshield.backend.fraud;

import com.fraudshield.backend.domain.model.CardStatus;
import com.fraudshield.backend.domain.model.TransactionChannel;
import com.fraudshield.backend.domain.model.TransactionDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FraudRulesEngine {

    public Optional<RuleEvaluationResult> evaluateHardStops(
            TransactionEvaluationRequest request, CardEvaluationProfile card) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (card.status() != CardStatus.ACTIVE
                || card.expiryDate().isBefore(request.transactionTimestamp().toLocalDate())) {
            reasons.add(ReasonCode.CARD_NOT_ACTIVE);
        }
        if (!request.transactionCountry().equals("IN") && !card.internationalEnabled()) {
            reasons.add(ReasonCode.INTL_NOT_ENABLED);
        }
        if (reasons.isEmpty()) return Optional.empty();
        return Optional.of(new RuleEvaluationResult(
                TransactionDecision.BLOCK, null, reasons, CalculatedTransactionFeatures.empty(), null));
    }

    public RuleEvaluationResult evaluate(
            TransactionEvaluationRequest request,
            CardEvaluationProfile card,
            MerchantEvaluationProfile merchant,
            boolean countryRiskHigh,
            CalculatedTransactionFeatures features,
            RuleConfiguration configuration) {
        List<ReasonCode> blocking = new ArrayList<>();
        if (merchant.risk().equals("BLACKLISTED")) blocking.add(ReasonCode.MERCHANT_BLACKLISTED);
        if (features.approvedToday().add(request.amount()).compareTo(card.dailyLimit()) > 0) {
            blocking.add(ReasonCode.DAILY_LIMIT_EXCEEDED);
        }
        if (features.velocity10Minutes() >= configuration.velocityBurstCount()) blocking.add(ReasonCode.VELOCITY_BURST);
        if (features.speedKilometresPerHour() != null
                && features.speedKilometresPerHour() > configuration.impossibleTravelThresholdKmph()) {
            blocking.add(ReasonCode.IMPOSSIBLE_TRAVEL);
        }
        if (!blocking.isEmpty()) {
            return new RuleEvaluationResult(TransactionDecision.BLOCK, 100, blocking, features, configuration.version());
        }

        List<ReasonCode> soft = new ArrayList<>();
        int score = 0;
        if (features.velocity10Minutes() >= configuration.highVelocityMinimum()
                && features.velocity10Minutes() <= configuration.highVelocityMaximum()) {
            soft.add(ReasonCode.HIGH_VELOCITY);
            score += configuration.highVelocityPoints();
        }
        if (amountSpike(request, features, configuration)) {
            soft.add(ReasonCode.AMOUNT_SPIKE);
            score += configuration.amountSpikePoints();
        }
        if (!request.transactionCountry().equals(card.homeCountry()) && countryRiskHigh) {
            soft.add(ReasonCode.HIGH_RISK_GEO);
            score += configuration.highRiskGeoPoints();
        }
        if (configuration.highRiskMccs().contains(request.mcc())) {
            soft.add(ReasonCode.HIGH_RISK_MCC);
            score += configuration.highRiskMccPoints();
        }
        int hour = features.aiFeatures().localHour();
        if (inHourRange(hour, configuration.oddHourStartInclusive(), configuration.oddHourEndInclusive())
                && request.amount().compareTo(configuration.oddHourMinimum()) >= 0) {
            soft.add(ReasonCode.ODD_HOUR_HIGH_VALUE);
            score += configuration.oddHourPoints();
        }
        if (request.channel() == TransactionChannel.ECOM && Boolean.TRUE.equals(features.newMerchant())
                && request.amount().compareTo(configuration.newMerchantMinimum()) >= 0) {
            soft.add(ReasonCode.NEW_MERCHANT_HIGH_VALUE);
            score += configuration.newMerchantPoints();
        }

        TransactionDecision decision;
        if (soft.size() >= configuration.multipleRedFlagsCount()) {
            soft.add(ReasonCode.MULTIPLE_RED_FLAGS);
            decision = TransactionDecision.BLOCK;
        } else if (!soft.isEmpty()) {
            decision = TransactionDecision.STEP_UP;
        } else {
            decision = TransactionDecision.APPROVE;
        }
        return new RuleEvaluationResult(decision, Math.min(score, 100), soft, features, configuration.version());
    }

    private boolean amountSpike(
            TransactionEvaluationRequest request,
            CalculatedTransactionFeatures features,
            RuleConfiguration configuration) {
        if (features.averageAmount30Days() == null) {
            return request.amount().compareTo(configuration.amountSpikeNoHistoryMinimum()) >= 0;
        }
        return request.amount().compareTo(features.averageAmount30Days().multiply(configuration.amountSpikeMultiplier())) > 0
                && request.amount().compareTo(configuration.amountSpikeMinimum()) >= 0;
    }

    private boolean inHourRange(int hour, int start, int end) {
        return start <= end ? hour >= start && hour <= end : hour >= start || hour <= end;
    }
}
