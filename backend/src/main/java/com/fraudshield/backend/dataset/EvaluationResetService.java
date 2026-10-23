package com.fraudshield.backend.dataset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationResetService {
    private final JdbcTemplate jdbc;

    public EvaluationResetService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public ResetSummary reset() {
        return jdbc.queryForObject(
                "SELECT deleted_transactions, restored_cards FROM reset_fraudshield_evaluation_data()",
                (rs, rowNum) -> new ResetSummary(rs.getLong("deleted_transactions"), rs.getLong("restored_cards")));
    }

    public record ResetSummary(long deletedTransactions, long restoredCards) {}
}
