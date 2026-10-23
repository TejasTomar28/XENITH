package com.fraudshield.backend.fraud;

import com.fraudshield.backend.domain.model.CardTransaction;
import com.fraudshield.backend.domain.model.City;
import com.fraudshield.backend.domain.model.TransactionChannel;
import com.fraudshield.backend.domain.model.TransactionStatus;
import com.fraudshield.backend.domain.repository.CardTransactionRepository;
import com.fraudshield.backend.domain.repository.CityRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionFeatureCalculator {
    static final double EARTH_RADIUS_KM = 6371.0;
    static final double ZERO_TIME_SPEED_KMPH = 99999.99;
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);
    private static final Set<TransactionChannel> PHYSICAL_CHANNELS =
            Set.of(TransactionChannel.POS, TransactionChannel.ATM, TransactionChannel.CONTACTLESS);

    private final CardTransactionRepository transactions;
    private final CityRepository cities;

    public TransactionFeatureCalculator(CardTransactionRepository transactions, CityRepository cities) {
        this.transactions = transactions;
        this.cities = cities;
    }

    @Transactional(readOnly = true)
    public CalculatedTransactionFeatures calculate(
            TransactionEvaluationRequest request,
            CardEvaluationProfile card,
            MerchantEvaluationProfile merchant,
            String mccCategory,
            boolean countryRiskHigh,
            RuleConfiguration configuration) {
        OffsetDateTime timestamp = request.transactionTimestamp();
        int velocity10Minutes = Math.toIntExact(transactions.countPriorTransactions(
                card.cardId(), timestamp.minusMinutes(configuration.velocityWindowMinutes()), timestamp));
        int velocity24Hours = Math.toIntExact(transactions.countPriorTransactions(
                card.cardId(), timestamp.minusHours(configuration.velocity24HourWindowHours()), timestamp));
        OffsetDateTime istDayStart = timestamp.withOffsetSameInstant(IST).toLocalDate().atStartOfDay().atOffset(IST);
        BigDecimal approvedToday = transactions.sumApprovedBeforeOnIstDay(
                card.cardId(), TransactionStatus.APPROVED, istDayStart, timestamp);
        Optional<BigDecimal> average = transactions.averageApprovedAmount(
                card.cardId(), TransactionStatus.APPROVED,
                timestamp.minusDays(configuration.amountAverageLookbackDays()), timestamp);
        Double speed = calculateTravelSpeed(request, card, configuration);
        boolean newMerchant = !transactions.existsPriorMerchantTransaction(
                card.cardId(), merchant.merchantId(), timestamp.minusDays(configuration.newMerchantLookbackDays()), timestamp);

        City transactionCity = requireCity(request.transactionCity(), request.transactionCountry());
        City homeCity = requireCity(card.homeCity(), card.homeCountry());
        double homeDistance = distanceKm(homeCity.getLatitude(), homeCity.getLongitude(),
                transactionCity.getLatitude(), transactionCity.getLongitude());
        BigDecimal ratio = average.filter(value -> value.signum() > 0)
                .map(value -> request.amount().divide(value, 12, RoundingMode.HALF_UP)).orElse(null);
        long cardAgeDays = ChronoUnit.DAYS.between(card.issueDate(), timestamp.withOffsetSameInstant(IST).toLocalDate());
        if (cardAgeDays < 0) throw new MissingReferenceDataException("Card issue date is after the transaction date");
        int localHour = timestamp.withOffsetSameInstant(IST).getHour();

        AiFeatureVector aiFeatures = new AiFeatureVector(
                card.cardType(), request.channel(), request.mcc(), mccCategory, merchant.risk(), request.amount(), localHour,
                !request.transactionCountry().equals(card.homeCountry()), countryRiskHigh, homeDistance,
                velocity10Minutes, velocity24Hours, ratio, newMerchant, cardAgeDays);
        return new CalculatedTransactionFeatures(
                velocity10Minutes, approvedToday, average.orElse(null), speed, newMerchant, aiFeatures);
    }

    private Double calculateTravelSpeed(
            TransactionEvaluationRequest request, CardEvaluationProfile card, RuleConfiguration configuration) {
        if (!PHYSICAL_CHANNELS.contains(request.channel())) return null;
        Optional<CardTransaction> previous = transactions
                .findFirstByCardIdAndStatusAndChannelInAndTransactionTimestampGreaterThanEqualAndTransactionTimestampLessThanOrderByTransactionTimestampDescTransactionIdDesc(
                        card.cardId(), TransactionStatus.APPROVED, PHYSICAL_CHANNELS,
                        request.transactionTimestamp().minusHours(configuration.impossibleTravelLookbackHours()),
                        request.transactionTimestamp());
        if (previous.isEmpty()) return null;
        CardTransaction prior = previous.orElseThrow();
        City previousCity = requireCity(prior.getTransactionCity(), prior.getTransactionCountry());
        City currentCity = requireCity(request.transactionCity(), request.transactionCountry());
        double distance = distanceKm(previousCity.getLatitude(), previousCity.getLongitude(),
                currentCity.getLatitude(), currentCity.getLongitude());
        return speedKmph(distance, prior.getTransactionTimestamp(), request.transactionTimestamp());
    }

    private City requireCity(String name, String country) {
        City city = cities.findById(name)
                .orElseThrow(() -> new MissingReferenceDataException("Missing city reference: " + name));
        if (!city.getCountry().getCountryCode().equals(country)) {
            throw new MissingReferenceDataException("City and country reference do not match: " + name + ", " + country);
        }
        return city;
    }

    static double distanceKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double fromLatitudeRadians = Math.toRadians(fromLatitude);
        double toLatitudeRadians = Math.toRadians(toLatitude);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(fromLatitudeRadians) * Math.cos(toLatitudeRadians)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
    }

    static double speedKmph(double distanceKm, OffsetDateTime previous, OffsetDateTime current) {
        if (distanceKm == 0.0) return 0.0;
        long elapsedNanos = Duration.between(previous.toInstant(), current.toInstant()).toNanos();
        if (elapsedNanos < 0) throw new IllegalArgumentException("Previous transaction must not be later than current transaction");
        if (elapsedNanos == 0) return ZERO_TIME_SPEED_KMPH;
        double hours = elapsedNanos / 3_600_000_000_000.0;
        return distanceKm / hours;
    }
}
