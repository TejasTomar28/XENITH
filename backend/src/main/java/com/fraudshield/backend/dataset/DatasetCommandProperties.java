package com.fraudshield.backend.dataset;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fraudshield.dataset")
public record DatasetCommandProperties(String command,Path directory,String customerSeedPassword,Path input,Path outputDirectory) {}
