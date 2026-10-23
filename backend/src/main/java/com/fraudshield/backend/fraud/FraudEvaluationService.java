package com.fraudshield.backend.fraud;

import com.fraudshield.backend.domain.model.Card;
import com.fraudshield.backend.domain.model.CountryRisk;
import com.fraudshield.backend.domain.model.Merchant;
import com.fraudshield.backend.domain.model.MerchantCategoryCode;
import com.fraudshield.backend.domain.repository.CardRepository;
import com.fraudshield.backend.domain.repository.CityRepository;
import com.fraudshield.backend.domain.repository.CountryRiskRepository;
import com.fraudshield.backend.domain.repository.MerchantCategoryCodeRepository;
import com.fraudshield.backend.domain.repository.MerchantRepository;
import com.fraudshield.backend.domain.model.TransactionSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudEvaluationService {
    private final CardRepository cards;
    private final MerchantRepository merchants;
    private final MerchantCategoryCodeRepository mccCodes;
    private final CityRepository cities;
    private final CountryRiskRepository countries;
    private final PersistedRuleConfigurationProvider configurations;
    private final TransactionFeatureCalculator featureCalculator;
    private final FraudRulesEngine rulesEngine;

    public FraudEvaluationService(
            CardRepository cards,
            MerchantRepository merchants,
            MerchantCategoryCodeRepository mccCodes,
            CityRepository cities,
            CountryRiskRepository countries,
            PersistedRuleConfigurationProvider configurations,
            TransactionFeatureCalculator featureCalculator,
            FraudRulesEngine rulesEngine) {
        this.cards = cards;
        this.merchants = merchants;
        this.mccCodes = mccCodes;
        this.cities = cities;
        this.countries = countries;
        this.configurations = configurations;
        this.featureCalculator = featureCalculator;
        this.rulesEngine = rulesEngine;
    }

    @Transactional(readOnly = true)
    public RuleEvaluationResult evaluate(TransactionEvaluationRequest request) {
        return evaluate(request,null);
    }

    @Transactional(readOnly = true)
    public RuleEvaluationResult evaluateBatch(TransactionEvaluationRequest request,TransactionSource source) {
        if(source!=TransactionSource.BATCH_SAMPLE&&source!=TransactionSource.EVALUATION)throw new IllegalArgumentException("Unsupported batch source");
        return evaluate(request,source);
    }

    private RuleEvaluationResult evaluate(TransactionEvaluationRequest request,TransactionSource source) {
        Card cardEntity = cards.findById(request.cardId())
                .orElseThrow(() -> new MissingReferenceDataException("Unknown card: " + request.cardId()));
        CardEvaluationProfile card = new CardEvaluationProfile(
                cardEntity.getId(), source==null?cardEntity.getStatus():cardEntity.getSeedStatus(), cardEntity.getCardType(), cardEntity.getIssueDate(),
                cardEntity.getExpiryDate(), cardEntity.getHomeCity(), cardEntity.getHomeCountry(),
                cardEntity.getDailyLimit(), cardEntity.isInternationalEnabled());

        var hardStop = rulesEngine.evaluateHardStops(request, card);
        if (hardStop.isPresent()) return hardStop.orElseThrow();

        Merchant merchantEntity = merchants.findById(request.merchantId())
                .orElseThrow(() -> new MissingReferenceDataException("Unknown merchant: " + request.merchantId()));
        MerchantEvaluationProfile merchant = new MerchantEvaluationProfile(merchantEntity.getId(), merchantEntity.getRisk());
        MerchantCategoryCode mcc = mccCodes.findById(request.mcc())
                .orElseThrow(() -> new MissingReferenceDataException("Unknown MCC: " + request.mcc()));
        var transactionCity = cities.findById(request.transactionCity())
                .orElseThrow(() -> new MissingReferenceDataException("Unknown transaction city: " + request.transactionCity()));
        if (!transactionCity.getCountry().getCountryCode().equals(request.transactionCountry())) {
            throw new MissingReferenceDataException("Transaction city and country do not match");
        }
        CountryRisk country = countries.findById(request.transactionCountry())
                .orElseThrow(() -> new MissingReferenceDataException("Unknown country: " + request.transactionCountry()));
        RuleConfiguration configuration = configurations.current();
        CalculatedTransactionFeatures features = source==null?featureCalculator.calculate(
                request,card,merchant,mcc.getCategory(),country.getRiskLevel().equals("HIGH"),configuration)
                :featureCalculator.calculateForBatch(request,card,merchant,mcc.getCategory(),country.getRiskLevel().equals("HIGH"),configuration,source);
        return rulesEngine.evaluate(
                request, card, merchant, country.getRiskLevel().equals("HIGH"), features, configuration);
    }
}
