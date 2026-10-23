package com.fraudshield.backend.authorization.ai;

import java.math.BigDecimal;
import java.util.List;

public record ModelEvaluation(
        ModelStatus status,
        BigDecimal fraudScore,
        List<TopFactor> topFactors,
        String modelVersion) {

    public ModelEvaluation {
        topFactors = List.copyOf(topFactors);
    }

    public static ModelEvaluation unavailable() {
        return new ModelEvaluation(ModelStatus.MODEL_UNAVAILABLE, null, List.of(), null);
    }

    public static ModelEvaluation notEvaluated() {
        return new ModelEvaluation(ModelStatus.NOT_EVALUATED, null, List.of(), null);
    }
}
