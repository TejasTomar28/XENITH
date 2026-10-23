package com.fraudshield.backend.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "evaluation"})
@ConditionalOnProperty(prefix = "fraudshield.dataset", name = "command")
public class DatasetCommandRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetCommandRunner.class);
    private final DatasetCommandProperties properties;
    private final OfficialDatasetImporter importer;
    private final EvaluationResetService resetService;

    public DatasetCommandRunner(DatasetCommandProperties properties, OfficialDatasetImporter importer, EvaluationResetService resetService) {
        this.properties = properties;
        this.importer = importer;
        this.resetService = resetService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String command = properties.command() == null ? "" : properties.command().trim().toLowerCase(java.util.Locale.ROOT);
        if (command.equals("seed")) {
            if (properties.directory() == null) throw new DatasetImportException("fraudshield.dataset.directory is required for seed");
            ImportSummary summary = importer.importOfficialData(properties.directory(), properties.customerSeedPassword());
            LOGGER.info("Official dataset seed completed: inserted={}, skipped={}, rejected={}", summary.inserted(), summary.skipped(), summary.rejected());
        } else if (command.equals("reset")) {
            EvaluationResetService.ResetSummary summary = resetService.reset();
            LOGGER.info("Evaluation reset completed: deletedTransactions={}, restoredCards={}", summary.deletedTransactions(), summary.restoredCards());
        } else {
            throw new DatasetImportException("fraudshield.dataset.command must be seed or reset");
        }
    }
}
