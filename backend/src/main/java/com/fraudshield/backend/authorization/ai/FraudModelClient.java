package com.fraudshield.backend.authorization.ai;

import com.fraudshield.backend.fraud.AiFeatureVector;

public interface FraudModelClient {
    ModelEvaluation evaluate(AiFeatureVector features);
}
