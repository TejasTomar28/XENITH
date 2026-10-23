package com.fraudshield.backend.authorization.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.domain.model.CardType;
import com.fraudshield.backend.domain.model.TransactionChannel;
import com.fraudshield.backend.fraud.AiFeatureVector;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpFraudModelClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOnlyContractFeaturesAndAcceptsValidPrediction() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> internalKey = new AtomicReference<>();
        start(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            internalKey.set(exchange.getRequestHeaders().getFirst(HttpFraudModelClient.INTERNAL_API_KEY_HEADER));
            respond(exchange, 200, """
                    {"fraud_score":0.82,"model_version":"model-v1","top_factors":[
                    {"feature":"amount","explanation":"Amount contributed"},
                    {"feature":"channel","explanation":"Channel contributed"},
                    {"feature":"velocity_10m","explanation":"Velocity contributed"}]}
                    """);
        });

        ModelEvaluation result = client(150).evaluate(features());

        assertThat(result.status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(result.fraudScore()).isEqualByComparingTo("0.82");
        assertThat(result.topFactors()).hasSize(3);
        assertThat(internalKey).hasValue("internal-test-key");
        assertThat(requestBody.get()).contains("\"card_type\"", "\"velocity_10m\"", "\"is_international\"")
                .doesNotContain("txn_id", "card_id", "customer", "mobile", "pan");
    }

    @Test
    void invalidPredictionFallsBackWithoutInventingScore() throws Exception {
        start(exchange -> respond(exchange, 200,
                "{\"fraud_score\":1.2,\"model_version\":\"model-v1\",\"top_factors\":[]}"));

        ModelEvaluation result = client(150).evaluate(features());

        assertThat(result.status()).isEqualTo(ModelStatus.MODEL_UNAVAILABLE);
        assertThat(result.fraudScore()).isNull();
        assertThat(result.topFactors()).isEmpty();
    }

    @Test
    void timeoutUsesFallbackWithinTotalBudget() throws Exception {
        start(exchange -> {
            try {
                Thread.sleep(300);
                respond(exchange, 200, "{}");
            } catch (Exception ignored) {
                exchange.close();
            }
        });
        Instant started = Instant.now();

        ModelEvaluation result = client(75).evaluate(features());

        assertThat(result.status()).isEqualTo(ModelStatus.MODEL_UNAVAILABLE);
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofMillis(250));
    }

    @Test
    void httpErrorMalformedAndMissingFieldsUseFallback() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        start(exchange->{
            int call=calls.getAndIncrement();
            if(call==0)respond(exchange,503,"{\"detail\":\"unavailable\"}");
            else if(call==1)respond(exchange,200,"not-json");
            else respond(exchange,200,"{\"fraud_score\":0.4}");
        });

        assertThat(client(150).evaluate(features()).status()).isEqualTo(ModelStatus.MODEL_UNAVAILABLE);
        assertThat(client(150).evaluate(features()).status()).isEqualTo(ModelStatus.MODEL_UNAVAILABLE);
        assertThat(client(150).evaluate(features()).status()).isEqualTo(ModelStatus.MODEL_UNAVAILABLE);
    }

    @Test
    void connectionRefusedUsesFallback() throws Exception {
        int unusedPort;
        try(ServerSocket socket=new ServerSocket(0)){unusedPort=socket.getLocalPort();}
        HttpFraudModelClient client=new HttpFraudModelClient(
                new AiClientProperties("http://localhost:"+unusedPort+"/predict","internal-test-key",150),
                new ObjectMapper());

        ModelEvaluation result=client.evaluate(features());

        assertThat(result.status()).isEqualTo(ModelStatus.MODEL_UNAVAILABLE);
        assertThat(result.fraudScore()).isNull();
    }

    private HttpFraudModelClient client(int timeoutMs) {
        return new HttpFraudModelClient(
                new AiClientProperties("http://localhost:" + server.getAddress().getPort() + "/predict",
                        "internal-test-key", timeoutMs),
                new ObjectMapper());
    }

    private AiFeatureVector features() {
        return new AiFeatureVector(CardType.DEBIT, TransactionChannel.ECOM, "5411", "RETAIL", "LOW",
                new BigDecimal("3500.00"), 14, false, false, 0, 1, 2,
                new BigDecimal("1.5"), false, 365);
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/predict", handler);
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
