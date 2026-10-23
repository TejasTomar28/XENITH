package com.fraudshield.backend.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.auth.ApplicationRole;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficialDatasetImporter {
    static final String COUNTRIES = "case_01_country_risk_V1.0.csv";
    static final String CITIES = "case_01_cities_V1.0.csv";
    static final String MCC = "case_01_mcc_codes_V1.0.csv";
    static final String CUSTOMERS = "case_01_customers_V1.0.csv";
    static final String CARDS = "case_01_cards_V1.0.csv";
    static final String MERCHANTS = "case_01_merchants_V1.0.csv";
    static final String RULES = "case_01_rules_config_V1.0.json";
    static final String HISTORY = "case_01_transactions_history_V1.0.csv";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public OfficialDatasetImporter(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportSummary importOfficialData(Path directory, String customerSeedPassword) {
        if (customerSeedPassword == null || customerSeedPassword.length() < 12) {
            throw new DatasetImportException("A customer seed password of at least 12 characters is required");
        }
        ImportSummary summary = new ImportSummary();
        importCsv(directory, COUNTRIES, Set.of("country_code", "country_name", "risk_level"), summary, this::insertCountry);
        importCsv(directory, CITIES, Set.of("city", "country", "lat", "lon", "city_tier"), summary, this::insertCity);
        importCsv(directory, MCC, Set.of("mcc", "mcc_description", "mcc_category", "high_risk"), summary, this::insertMcc);
        importCsv(directory, CUSTOMERS, Set.of("customer_id", "full_name", "mobile", "email", "city", "username"), summary,
                (row, number) -> insertCustomer(row, number, customerSeedPassword));
        importCsv(directory, CARDS, Set.of("card_id", "customer_id", "card_number", "card_type", "card_status", "issue_date",
                "expiry_date", "home_city", "home_country", "daily_limit", "international_enabled"), summary, this::insertCard);
        importCsv(directory, MERCHANTS, Set.of("merchant_id", "merchant_name", "mcc", "mcc_description", "city", "country", "merchant_risk"),
                summary, this::insertMerchant);
        importRules(directory, summary);
        importCsv(directory, HISTORY, Set.of("txn_id", "card_id", "txn_ts", "amount", "merchant_id", "mcc", "channel", "txn_city", "txn_country", "status"),
                summary, this::insertHistoricalTransaction);
        return summary;
    }

    private void importCsv(Path directory, String fileName, Set<String> expectedHeaders, ImportSummary summary,
            BiFunction<Map<String, String>, Long, Boolean> inserter) {
        Path path = requiredFile(directory, fileName);
        int inserted = 0;
        int skipped = 0;
        try {
            List<List<String>> records = readCsv(path);
            if (records.isEmpty()) throw new DatasetImportException(fileName + " is empty");
            List<String> headers = records.get(0);
            if (!Set.copyOf(headers).equals(expectedHeaders) || headers.size() != expectedHeaders.size()) {
                throw new DatasetImportException(fileName + " has unexpected headers; expected " + expectedHeaders + " but found " + headers);
            }
            for (int index = 1; index < records.size(); index++) {
                long rowNumber = index + 1L;
                List<String> values = records.get(index);
                if (values.size() != headers.size()) {
                    throw new DatasetImportException(fileName + " row " + rowNumber + " has " + values.size() + " columns; expected " + headers.size());
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) row.put(headers.get(column), values.get(column));
                try {
                    if (inserter.apply(row, rowNumber)) inserted++; else skipped++;
                } catch (DatasetImportException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new DatasetImportException(fileName + " row " + rowNumber + " is invalid: " + safeMessage(exception), exception);
                }
            }
        } catch (IOException exception) {
            throw new DatasetImportException("Unable to read " + fileName, exception);
        }
        summary.record(fileName, inserted, skipped);
    }

    private List<List<String>> readCsv(Path path) throws IOException {
        String content = Files.readString(path);
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
            } else if (current == '"' && field.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') index++;
                record.add(field.toString());
                field.setLength(0);
                if (!(record.size() == 1 && record.get(0).isBlank())) records.add(List.copyOf(record));
                record.clear();
            } else {
                field.append(current);
            }
        }
        if (quoted) throw new DatasetImportException(path.getFileName() + " contains an unterminated quoted field");
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(List.copyOf(record));
        }
        return records;
    }

    private Boolean insertCountry(Map<String, String> row, Long number) {
        String risk = required(row, "risk_level", number);
        allowed("risk_level", risk, Set.of("LOW", "HIGH"), number);
        return update("INSERT INTO country_risk(country_code,country_name,risk_level) VALUES (?,?,?) ON CONFLICT (country_code) DO NOTHING",
                required(row, "country_code", number), required(row, "country_name", number), risk);
    }

    private Boolean insertCity(Map<String, String> row, Long number) {
        String tier = required(row, "city_tier", number);
        allowed("city_tier", tier, Set.of("METRO", "NON_METRO", "INTL"), number);
        return update("INSERT INTO cities(city,country_code,latitude,longitude,city_tier) VALUES (?,?,?,?,?) ON CONFLICT (city) DO NOTHING",
                required(row, "city", number), required(row, "country", number), decimal(row, "lat", number), decimal(row, "lon", number), tier);
    }

    private Boolean insertMcc(Map<String, String> row, Long number) {
        String mcc = required(row, "mcc", number);
        if (!mcc.matches("[0-9]{4}")) invalid("mcc", number);
        return update("INSERT INTO mcc_codes(mcc,description,category,high_risk) VALUES (?,?,?,?) ON CONFLICT (mcc) DO NOTHING",
                mcc, required(row, "mcc_description", number), required(row, "mcc_category", number), yesNo(row, "high_risk", number));
    }

    private Boolean insertCustomer(Map<String, String> row, Long number, String password) {
        String customerId = required(row, "customer_id", number);
        if (exists("customers", "customer_id", customerId)) return false;
        String city = required(row, "city", number);
        requireReference("cities", "city", city, number);
        String username = required(row, "username", number).trim().toLowerCase(java.util.Locale.ROOT);
        UUID userId = jdbc.query("SELECT id FROM app_users WHERE username=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, username);
        if (userId == null) {
            userId = UUID.randomUUID();
            jdbc.update("INSERT INTO app_users(id,username,password_hash,role,enabled,created_at) VALUES (?,?,?,?,true,CURRENT_TIMESTAMP)",
                    userId, username, passwordEncoder.encode(password), ApplicationRole.CARDHOLDER.name());
        }
        return update("INSERT INTO customers(customer_id,user_id,full_name,mobile,email,city,username) VALUES (?,?,?,?,?,?,?) ON CONFLICT (customer_id) DO NOTHING",
                customerId, userId, required(row, "full_name", number), required(row, "mobile", number), required(row, "email", number), city, username);
    }

    private Boolean insertCard(Map<String, String> row, Long number) {
        String cardId = required(row, "card_id", number);
        if (exists("cards", "card_id", cardId)) return false;
        String customerId = required(row, "customer_id", number);
        requireReference("customers", "customer_id", customerId, number);
        String pan = required(row, "card_number", number);
        if (!pan.matches("9999[0-9]{12}")) throw new DatasetImportException("row " + number + " field card_number must be a 16-digit synthetic number starting 9999");
        String type = required(row, "card_type", number);
        String status = required(row, "card_status", number);
        allowed("card_type", type, Set.of("DEBIT", "CREDIT"), number);
        allowed("card_status", status, Set.of("ACTIVE", "BLOCKED", "EXPIRED"), number);
        return update("""
                INSERT INTO cards(card_id,customer_id,pan_hash,last_four,card_type,card_status,seed_card_status,issue_date,expiry_date,
                    home_city,home_country,daily_limit,international_enabled,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT (card_id) DO NOTHING
                """, cardId, customerId, passwordEncoder.encode(pan), pan.substring(12), type, status, status,
                date(row, "issue_date", number), date(row, "expiry_date", number), required(row, "home_city", number),
                required(row, "home_country", number), decimal(row, "daily_limit", number), yesNo(row, "international_enabled", number));
    }

    private Boolean insertMerchant(Map<String, String> row, Long number) {
        String mcc = required(row, "mcc", number);
        requireReference("mcc_codes", "mcc", mcc, number);
        String risk = required(row, "merchant_risk", number);
        allowed("merchant_risk", risk, Set.of("LOW", "MEDIUM", "HIGH", "BLACKLISTED"), number);
        return update("""
                INSERT INTO merchants(merchant_id,merchant_name,mcc,mcc_description,city,country,merchant_risk)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT (merchant_id) DO NOTHING
                """, required(row, "merchant_id", number), required(row, "merchant_name", number), mcc,
                required(row, "mcc_description", number), required(row, "city", number), required(row, "country", number), risk);
    }

    private Boolean insertHistoricalTransaction(Map<String, String> row, Long number) {
        String txnId = required(row, "txn_id", number);
        if (!txnId.matches("T[0-9]{9}")) invalid("txn_id", number);
        String status = required(row, "status", number);
        allowed("status", status, Set.of("APPROVED", "BLOCKED"), number);
        OffsetDateTime timestamp = timestamp(row, "txn_ts", number);
        if (!timestamp.getOffset().equals(java.time.ZoneOffset.ofHoursMinutes(5, 30))) {
            throw new DatasetImportException("row " + number + " field txn_ts must use +05:30 offset");
        }
        String channel = required(row, "channel", number);
        allowed("channel", channel, Set.of("POS", "ECOM", "ATM", "CONTACTLESS"), number);
        return update("""
                INSERT INTO transactions(id,txn_id,card_id,txn_ts,amount,currency,merchant_id,mcc,channel,txn_city,txn_country,status,
                    original_decision,original_status,transaction_source,resettable,processed_at,version)
                VALUES (?,?,?,?,?,'INR',?,?,?,?,?,?,?,?,'HISTORICAL',false,?,0) ON CONFLICT (txn_id) DO NOTHING
                """, UUID.randomUUID(), txnId, required(row, "card_id", number), timestamp, decimal(row, "amount", number),
                required(row, "merchant_id", number), required(row, "mcc", number), channel, required(row, "txn_city", number),
                required(row, "txn_country", number), status, status.equals("APPROVED") ? "APPROVE" : "BLOCK", status, timestamp);
    }

    private void importRules(Path directory, ImportSummary summary) {
        Path path = requiredFile(directory, RULES);
        try {
            byte[] bytes = Files.readAllBytes(path);
            JsonNode configuration = objectMapper.readTree(bytes);
            if (configuration == null || !configuration.isObject() || configuration.isEmpty()) {
                throw new DatasetImportException(RULES + " must contain a non-empty JSON object");
            }
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (exists("rule_configuration_versions", "source_sha256", checksum)) {
                summary.record(RULES, 0, 1);
                return;
            }
            jdbc.update("UPDATE rule_configuration_versions SET active=false WHERE active");
            jdbc.update("INSERT INTO rule_configuration_versions(id,source_file,source_sha256,configuration,active,imported_at,imported_by) VALUES (?,?,?,?::jsonb,true,CURRENT_TIMESTAMP,?)",
                    UUID.randomUUID(), RULES, checksum, configuration.toString(), "dataset-import");
            summary.record(RULES, 1, 0);
        } catch (DatasetImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DatasetImportException("Unable to import " + RULES, exception);
        }
    }

    private Path requiredFile(Path directory, String fileName) {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path path = normalizedDirectory.resolve(fileName).normalize();
        if (!path.startsWith(normalizedDirectory) || !Files.isRegularFile(path)) {
            throw new DatasetImportException("Required dataset file is missing: " + fileName);
        }
        return path;
    }

    private String required(Map<String, String> row, String field, long number) {
        String value = row.get(field);
        if (value == null || value.isBlank()) throw new DatasetImportException("row " + number + " field " + field + " is required");
        return value.trim();
    }

    private BigDecimal decimal(Map<String, String> row, String field, long number) {
        try { return new BigDecimal(required(row, field, number)); }
        catch (NumberFormatException exception) { throw new DatasetImportException("row " + number + " field " + field + " must be numeric"); }
    }

    private LocalDate date(Map<String, String> row, String field, long number) {
        try { return LocalDate.parse(required(row, field, number)); }
        catch (Exception exception) { throw new DatasetImportException("row " + number + " field " + field + " must be ISO date"); }
    }

    private OffsetDateTime timestamp(Map<String, String> row, String field, long number) {
        try { return OffsetDateTime.parse(required(row, field, number)); }
        catch (Exception exception) { throw new DatasetImportException("row " + number + " field " + field + " must be ISO-8601 with offset"); }
    }

    private boolean yesNo(Map<String, String> row, String field, long number) {
        String value = required(row, field, number);
        if (value.equals("Y")) return true;
        if (value.equals("N")) return false;
        throw new DatasetImportException("row " + number + " field " + field + " must be Y or N");
    }

    private void allowed(String field, String value, Set<String> allowed, long number) {
        if (!allowed.contains(value)) throw new DatasetImportException("row " + number + " field " + field + " has unsupported value");
    }

    private void invalid(String field, long number) { throw new DatasetImportException("row " + number + " field " + field + " has invalid format"); }
    private boolean update(String sql, Object... args) { return jdbc.update(sql, args) == 1; }
    private boolean exists(String table, String column, Object value) {
        List<String> allowedTables = List.of("customers", "cards", "cities", "mcc_codes", "rule_configuration_versions");
        if (!allowedTables.contains(table)) throw new IllegalArgumentException("Unsupported lookup table");
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + "=?", Integer.class, value);
        return count != null && count > 0;
    }
    private void requireReference(String table, String column, Object value, long row) {
        if (!exists(table, column, value)) throw new DatasetImportException("row " + row + " references missing " + table + "." + column);
    }
    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.replaceAll("(?i)9999[0-9]{12}", "[REDACTED_PAN]");
    }
}
