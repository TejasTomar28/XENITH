package com.fraudshield.backend.config;

import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.auth.UserAccountRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(20)
@ConditionalOnProperty(prefix = "fraudshield.demo-data", name = "enabled", havingValue = "true")
public class DevelopmentDemoDataSeeder implements ApplicationRunner {
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);
    private static final String CONFIG_VERSION = "demo000000000000000000000000000000000000000000000000000000000000";
    private static final String CONFIG = """
            {"velocity_window_minutes":10,"velocity_24h_window_hours":24,"velocity_burst_count":5,
            "high_velocity_min_count":3,"high_velocity_max_count":4,"amount_average_lookback_days":30,
            "amount_spike_multiplier":5,"amount_spike_minimum":10000,"amount_spike_no_history_minimum":50000,
            "impossible_travel_threshold_kmph":800,"impossible_travel_lookback_hours":6,
            "high_velocity_points":25,"amount_spike_points":30,"high_risk_geo_points":25,"high_risk_mcc_points":15,
            "odd_hour_points":15,"new_merchant_points":20,"high_risk_mccs":["4829","5967","6051","7995"],
            "odd_hour_start_inclusive":0,"odd_hour_end_inclusive":4,"odd_hour_minimum":20000,
            "new_merchant_lookback_days":90,"new_merchant_minimum":25000,"multiple_red_flags_count":3,
            "alert_high_amount":50000,"alert_high_sla_minutes":30,"alert_medium_sla_minutes":240,
            "step_up_expiry_minutes":15,"maker_checker_limit":100000,
            "ai_step_up_threshold":0.70,"ai_block_threshold":0.90}
            """;

    private final JdbcTemplate jdbc;
    private final UserAccountRepository users;
    private final DevelopmentUserProperties devUsers;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DevelopmentDemoDataSeeder(JdbcTemplate jdbc, UserAccountRepository users, DevelopmentUserProperties devUsers,
            PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.devUsers = devUsers;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID cardholder = users.findByUsername(UserAccount.normalizeUsername(devUsers.cardholderUsername()))
                .orElseThrow(() -> new IllegalStateException("Enable development users before demo data"))
                .getId();
        jdbc.update("INSERT INTO country_risk VALUES ('IN','India','LOW') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO cities VALUES ('Mumbai','IN',19.0760,72.8777,'METRO') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO mcc_codes VALUES ('5411','Grocery','RETAIL',false) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO mcc_codes VALUES ('7995','Betting','ENTERTAINMENT',true) ON CONFLICT DO NOTHING");
        jdbc.update("""
                INSERT INTO customers(customer_id,user_id,full_name,mobile,email,city,username)
                VALUES ('DEMO-CUSTOMER',?,'Demo Cardholder','0000000000','demo@example.test','Mumbai',?)
                ON CONFLICT (customer_id) DO UPDATE SET user_id=EXCLUDED.user_id,username=EXCLUDED.username
                """, cardholder, UserAccount.normalizeUsername(devUsers.cardholderUsername()));
        jdbc.update("""
                INSERT INTO cards(card_id,customer_id,pan_hash,last_four,card_type,card_status,seed_card_status,
                  issue_date,expiry_date,home_city,home_country,daily_limit,international_enabled)
                VALUES ('CARD1','DEMO-CUSTOMER',?,'4242','DEBIT','ACTIVE','ACTIVE','2024-01-01','2030-12-31',
                  'Mumbai','IN',500000,true) ON CONFLICT DO NOTHING
                """, encoder.encode("fraudshield-demo-card"));
        jdbc.update("INSERT INTO merchants VALUES ('MER1','Demo Grocery','5411','Grocery','Mumbai','IN','LOW') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO merchants VALUES ('MER_RISK','Demo High Risk Merchant','7995','Betting','Mumbai','IN','HIGH') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO merchants VALUES ('MER_BLOCK','Demo Blocklisted Merchant','5411','Grocery','Mumbai','IN','BLACKLISTED') ON CONFLICT DO NOTHING");
        Boolean active = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM rule_configuration_versions WHERE active)", Boolean.class);
        if (!Boolean.TRUE.equals(active)) {
            jdbc.update("""
                    INSERT INTO rule_configuration_versions(id,source_file,source_sha256,configuration,active,imported_at,imported_by)
                    VALUES (?,'demo-rules.json',?,?::jsonb,true,?,'demo-seeder')
                    """, UUID.randomUUID(), CONFIG_VERSION, CONFIG, now());
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(IST);
    }
}
