package com.fraudshield.backend.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudshield.backend.domain.model.CardStatus;
import com.fraudshield.backend.domain.model.CardType;
import com.fraudshield.backend.domain.model.TransactionChannel;
import com.fraudshield.backend.domain.model.TransactionDecision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FraudRulesEngineTest {
    private final FraudRulesEngine engine = new FraudRulesEngine();

    @Test
    void r1BlocksInactiveCardWithoutFeaturesOrScore() {
        RuleEvaluationResult result = engine.evaluateHardStops(request("3500", "IN", "5732", TransactionChannel.POS, 14),
                card(CardStatus.BLOCKED, LocalDate.of(2029, 12, 31), false)).orElseThrow();
        assertThat(result.decision()).isEqualTo(TransactionDecision.BLOCK);
        assertThat(result.reasonCodes()).containsExactly(ReasonCode.CARD_NOT_ACTIVE);
        assertThat(result.ruleRiskScore()).isNull();
        assertThat(result.features()).isEqualTo(CalculatedTransactionFeatures.empty());
    }

    @Test
    void r1BlocksExpiredCard() {
        RuleEvaluationResult result = engine.evaluateHardStops(request("3500", "IN", "5732", TransactionChannel.POS, 14),
                card(CardStatus.ACTIVE, LocalDate.of(2026, 3, 3), false)).orElseThrow();
        assertThat(result.reasonCodes()).containsExactly(ReasonCode.CARD_NOT_ACTIVE);
    }

    @Test
    void r2BlocksInternationalWhenDisabledAndKeepsHardStopOrder() {
        RuleEvaluationResult result = engine.evaluateHardStops(request("3500", "SG", "5732", TransactionChannel.POS, 14),
                card(CardStatus.BLOCKED, LocalDate.of(2029, 12, 31), false)).orElseThrow();
        assertThat(result.reasonCodes()).containsExactly(ReasonCode.CARD_NOT_ACTIVE, ReasonCode.INTL_NOT_ENABLED);
    }

    @Test
    void r3BlocksBlacklistedMerchant() {
        assertBlocking(merchant("BLACKLISTED"), features(0, "0", "2400", null, false, 14), false,
                ReasonCode.MERCHANT_BLACKLISTED);
    }

    @Test
    void r4UsesStrictBigDecimalDailyLimitComparison() {
        RuleEvaluationResult boundary = evaluate(request("8000.00", "IN", "5732", TransactionChannel.POS, 14),
                card(CardStatus.ACTIVE, LocalDate.of(2029, 12, 31), false), merchant("LOW"), false,
                features(0, "92000.00", "2400", null, false, 14), defaults());
        RuleEvaluationResult exceeded = evaluate(request("8000.01", "IN", "5732", TransactionChannel.POS, 14),
                card(CardStatus.ACTIVE, LocalDate.of(2029, 12, 31), false), merchant("LOW"), false,
                features(0, "92000.00", "2400", null, false, 14), defaults());
        assertThat(boundary.reasonCodes()).doesNotContain(ReasonCode.DAILY_LIMIT_EXCEEDED);
        assertThat(exceeded.reasonCodes()).contains(ReasonCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void r5BlocksAtConfiguredVelocityBurstThreshold() {
        assertBlocking(merchant("LOW"), features(5, "0", "2400", null, false, 14), false, ReasonCode.VELOCITY_BURST);
    }

    @Test
    void r6UsesUnroundedTravelSpeed() {
        assertBlocking(merchant("LOW"), features(0, "0", "2400", 800.0001, false, 14), false, ReasonCode.IMPOSSIBLE_TRAVEL);
    }

    @Test
    void blockingRulesReturnEveryApplicableCodeInRuleOrderAndSkipSoftRules() {
        RuleEvaluationResult result = evaluate(request("60000", "XA", "6051", TransactionChannel.ECOM, 2),
                card(CardStatus.ACTIVE, LocalDate.of(2029, 12, 31), true), merchant("BLACKLISTED"), true,
                features(5, "50000", null, 900.0, true, 2), defaults());
        assertThat(result.reasonCodes()).containsExactly(
                ReasonCode.MERCHANT_BLACKLISTED, ReasonCode.DAILY_LIMIT_EXCEEDED,
                ReasonCode.VELOCITY_BURST, ReasonCode.IMPOSSIBLE_TRAVEL);
        assertThat(result.ruleRiskScore()).isEqualTo(100);
    }

    @Test
    void r7TriggersForConfiguredHighVelocityRange() {
        assertStepUp(features(3, "0", "2400", null, false, 14), request("1200", "IN", "5732", TransactionChannel.ECOM, 14),
                ReasonCode.HIGH_VELOCITY, 25);
    }

    @Test
    void r8RequiresStrictlyGreaterThanMultiplierWithHistory() {
        RuleEvaluationResult boundary = evaluate(request("12000.00", "IN", "5732", TransactionChannel.ECOM, 14),
                activeCard(), merchant("LOW"), false, features(0, "0", "2400.00", null, false, 14), defaults());
        RuleEvaluationResult above = evaluate(request("12000.01", "IN", "5732", TransactionChannel.ECOM, 14),
                activeCard(), merchant("LOW"), false, features(0, "0", "2400.00", null, false, 14), defaults());
        assertThat(boundary.reasonCodes()).doesNotContain(ReasonCode.AMOUNT_SPIKE);
        assertThat(above.reasonCodes()).contains(ReasonCode.AMOUNT_SPIKE);
    }

    @Test
    void r8UsesNoHistoryThresholdAndDoesNotSubstituteZeroAverage() {
        assertStepUp(features(0, "0", null, null, false, 14), request("50000", "IN", "5732", TransactionChannel.POS, 14),
                ReasonCode.AMOUNT_SPIKE, 30);
    }

    @Test
    void r9RequiresDifferentHomeCountryAndHighRiskDestination() {
        RuleEvaluationResult result = evaluate(request("5000", "XA", "5732", TransactionChannel.POS, 14),
                card(CardStatus.ACTIVE, LocalDate.of(2029, 12, 31), true), merchant("LOW"), true,
                features(0, "0", "2400", null, false, 14), defaults());
        assertThat(result.reasonCodes()).containsExactly(ReasonCode.HIGH_RISK_GEO);
    }

    @Test
    void r10UsesConfiguredMccList() {
        assertStepUp(features(0, "0", "2400", null, false, 14), request("5000", "IN", "6051", TransactionChannel.POS, 14),
                ReasonCode.HIGH_RISK_MCC, 15);
    }

    @Test
    void r11IncludesFourFiftyNineAndMinimumAmount() {
        assertStepUp(features(0, "0", "5000", null, false, 4), request("20000", "IN", "5732", TransactionChannel.POS, 4),
                ReasonCode.ODD_HOUR_HIGH_VALUE, 15);
    }

    @Test
    void r12RequiresEcommerceNewMerchantAndMinimumAmount() {
        assertStepUp(features(0, "0", "10000", null, true, 14), request("25000", "IN", "5732", TransactionChannel.ECOM, 14),
                ReasonCode.NEW_MERCHANT_HIGH_VALUE, 20);
    }

    @Test
    void r13BlocksThreeOrMoreSoftRulesAndPreservesScore() {
        RuleEvaluationResult result = evaluate(request("45000", "IN", "6051", TransactionChannel.ECOM, 2), activeCard(), merchant("LOW"), false,
                features(0, "0", "2400", null, true, 2), defaults());
        assertThat(result.decision()).isEqualTo(TransactionDecision.BLOCK);
        assertThat(result.reasonCodes()).containsExactly(
                ReasonCode.AMOUNT_SPIKE, ReasonCode.HIGH_RISK_MCC, ReasonCode.ODD_HOUR_HIGH_VALUE,
                ReasonCode.NEW_MERCHANT_HIGH_VALUE, ReasonCode.MULTIPLE_RED_FLAGS);
        assertThat(result.ruleRiskScore()).isEqualTo(80);
    }

    @Test
    void returnsApproveWhenNoRuleTriggers() {
        RuleEvaluationResult result = evaluate(request("3500", "IN", "5732", TransactionChannel.POS, 14), activeCard(), merchant("LOW"), false,
                features(0, "0", "2400", null, false, 14), defaults());
        assertThat(result.decision()).isEqualTo(TransactionDecision.APPROVE);
        assertThat(result.ruleRiskScore()).isZero();
        assertThat(result.reasonCodes()).isEmpty();
    }

    @Test
    void changedConfigurationImmediatelyChangesEvaluation() {
        RuleConfiguration changed = configWithBurst(2);
        RuleEvaluationResult result = evaluate(request("1000", "IN", "5732", TransactionChannel.POS, 14), activeCard(), merchant("LOW"), false,
                features(2, "0", "2400", null, false, 14), changed);
        assertThat(result.decision()).isEqualTo(TransactionDecision.BLOCK);
        assertThat(result.reasonCodes()).containsExactly(ReasonCode.VELOCITY_BURST);
        assertThat(result.configurationVersion()).isEqualTo("changed");
    }

    @Test
    void softScoreIsCappedAtOneHundred() {
        RuleConfiguration highPoints = new RuleConfiguration("high-points", 10, 24, 5, 3, 4, 30,
                new BigDecimal("5"), new BigDecimal("10000"), new BigDecimal("50000"), 800, 6,
                60, 60, 60, 60, 60, 60, Set.of("6051"), 0, 4,
                new BigDecimal("20000"), 90, new BigDecimal("25000"), 3,
                new BigDecimal("50000"), 30, 240, 15, new BigDecimal("100000"), new BigDecimal("0.70"), new BigDecimal("0.90"));
        RuleEvaluationResult result = evaluate(request("45000", "IN", "6051", TransactionChannel.ECOM, 2), activeCard(), merchant("LOW"), false,
                features(3, "0", "2400", null, true, 2), highPoints);
        assertThat(result.ruleRiskScore()).isEqualTo(100);
    }

    private void assertBlocking(MerchantEvaluationProfile merchant, CalculatedTransactionFeatures features,
            boolean countryHigh, ReasonCode reason) {
        RuleEvaluationResult result = evaluate(request("3500", "IN", "5732", TransactionChannel.POS, 14), activeCard(), merchant, countryHigh, features, defaults());
        assertThat(result.decision()).isEqualTo(TransactionDecision.BLOCK);
        assertThat(result.reasonCodes()).contains(reason);
        assertThat(result.ruleRiskScore()).isEqualTo(100);
    }

    private void assertStepUp(CalculatedTransactionFeatures features, TransactionEvaluationRequest request, ReasonCode reason, int score) {
        RuleEvaluationResult result = evaluate(request, activeCard(), merchant("LOW"), false, features, defaults());
        assertThat(result.decision()).isEqualTo(TransactionDecision.STEP_UP);
        assertThat(result.reasonCodes()).containsExactly(reason);
        assertThat(result.ruleRiskScore()).isEqualTo(score);
    }

    private RuleEvaluationResult evaluate(TransactionEvaluationRequest request, CardEvaluationProfile card,
            MerchantEvaluationProfile merchant, boolean countryHigh, CalculatedTransactionFeatures features,
            RuleConfiguration configuration) {
        return engine.evaluate(request, card, merchant, countryHigh, features, configuration);
    }

    private TransactionEvaluationRequest request(String amount, String country, String mcc, TransactionChannel channel, int hour) {
        return new TransactionEvaluationRequest("T900000001", "CARD1",
                OffsetDateTime.parse("2026-03-04T" + String.format("%02d", hour) + ":05:00+05:30"),
                new BigDecimal(amount), "INR", "MER1", mcc, channel, "Mumbai", country);
    }

    private CardEvaluationProfile activeCard() {
        return card(CardStatus.ACTIVE, LocalDate.of(2029, 12, 31), false);
    }

    private CardEvaluationProfile card(CardStatus status, LocalDate expiry, boolean international) {
        return new CardEvaluationProfile("CARD1", status, CardType.DEBIT, LocalDate.of(2025, 1, 1), expiry,
                "Mumbai", "IN", new BigDecimal("100000.00"), international);
    }

    private MerchantEvaluationProfile merchant(String risk) { return new MerchantEvaluationProfile("MER1", risk); }

    private CalculatedTransactionFeatures features(int velocity, String approved, String average, Double speed, boolean newMerchant, int hour) {
        BigDecimal averageValue = average == null ? null : new BigDecimal(average);
        AiFeatureVector ai = new AiFeatureVector(CardType.DEBIT, TransactionChannel.POS, "5732", "RETAIL", "LOW",
                new BigDecimal("1"), hour, false, false, 0, velocity, velocity, null, newMerchant, 365);
        return new CalculatedTransactionFeatures(velocity, new BigDecimal(approved), averageValue, speed, newMerchant, ai);
    }

    static RuleConfiguration defaults() {
        return new RuleConfiguration("defaults", 10, 24, 5, 3, 4, 30,
                new BigDecimal("5"), new BigDecimal("10000"), new BigDecimal("50000"), 800, 6,
                25, 30, 25, 15, 15, 20, Set.of("4829", "5967", "6051", "7995"),
                0, 4, new BigDecimal("20000"), 90, new BigDecimal("25000"), 3,
                new BigDecimal("50000"), 30, 240, 15, new BigDecimal("100000"), new BigDecimal("0.70"), new BigDecimal("0.90"));
    }

    private RuleConfiguration configWithBurst(int burst) {
        RuleConfiguration base = defaults();
        return new RuleConfiguration("changed", base.velocityWindowMinutes(), base.velocity24HourWindowHours(), burst,
                base.highVelocityMinimum(), base.highVelocityMaximum(), base.amountAverageLookbackDays(),
                base.amountSpikeMultiplier(), base.amountSpikeMinimum(), base.amountSpikeNoHistoryMinimum(),
                base.impossibleTravelThresholdKmph(), base.impossibleTravelLookbackHours(), base.highVelocityPoints(),
                base.amountSpikePoints(), base.highRiskGeoPoints(), base.highRiskMccPoints(), base.oddHourPoints(),
                base.newMerchantPoints(), base.highRiskMccs(), base.oddHourStartInclusive(), base.oddHourEndInclusive(),
                base.oddHourMinimum(), base.newMerchantLookbackDays(), base.newMerchantMinimum(), base.multipleRedFlagsCount(),
                base.alertHighAmount(), base.alertHighSlaMinutes(), base.alertMediumSlaMinutes(),
                base.stepUpExpiryMinutes(), base.makerCheckerLimit(),
                base.aiStepUpThreshold(), base.aiBlockThreshold());
    }
}
