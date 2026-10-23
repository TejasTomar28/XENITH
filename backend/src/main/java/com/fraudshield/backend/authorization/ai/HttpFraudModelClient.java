package com.fraudshield.backend.authorization.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fraudshield.backend.fraud.AiFeatureVector;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class HttpFraudModelClient implements FraudModelClient {
    static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";

    private final AiClientProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public HttpFraudModelClient(AiClientProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .build());
    }

    HttpFraudModelClient(AiClientProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public ModelEvaluation evaluate(AiFeatureVector features) {
        if (properties.internalApiKey() == null || properties.internalApiKey().isBlank()) {
            return ModelEvaluation.unavailable();
        }
        try {
            ObjectNode featureJson = objectMapper.valueToTree(features);
            ObjectNode body = objectMapper.createObjectNode().set("features", featureJson);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.predictionUrl()))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header(INTERNAL_API_KEY_HEADER, properties.internalApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(properties.timeoutMs(), TimeUnit.MILLISECONDS)
                    .join();
            if (response.statusCode() != 200) return ModelEvaluation.unavailable();
            return decode(response.body());
        } catch (Exception exception) {
            return ModelEvaluation.unavailable();
        }
    }

    private ModelEvaluation decode(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode scoreNode = root.get("fraud_score");
        JsonNode versionNode = root.get("model_version");
        JsonNode factorsNode = root.get("top_factors");
        if (scoreNode == null || !scoreNode.isNumber() || !Double.isFinite(scoreNode.doubleValue())
                || scoreNode.doubleValue() < 0 || scoreNode.doubleValue() > 1
                || versionNode == null || !versionNode.isTextual() || versionNode.asText().isBlank()
                || factorsNode == null || !factorsNode.isArray() || factorsNode.size() != 3) {
            return ModelEvaluation.unavailable();
        }
        List<TopFactor> factors = new ArrayList<>();
        for (JsonNode item : factorsNode) {
            JsonNode feature = item.get("feature");
            JsonNode explanation = item.get("explanation");
            if (feature == null || !feature.isTextual() || feature.asText().isBlank()
                    || explanation == null || !explanation.isTextual() || explanation.asText().isBlank()) {
                return ModelEvaluation.unavailable();
            }
            JsonNode contribution = item.get("contribution");
            if (contribution != null && (!contribution.isNumber() || !Double.isFinite(contribution.doubleValue()))) {
                return ModelEvaluation.unavailable();
            }
            factors.add(new TopFactor(feature.asText(), explanation.asText(),
                    contribution == null ? null : contribution.doubleValue()));
        }
        return new ModelEvaluation(ModelStatus.AVAILABLE, scoreNode.decimalValue(), factors, versionNode.asText());
    }
}
