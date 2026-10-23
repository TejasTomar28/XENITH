package com.fraudshield.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fraudshield.backend.auth.ApplicationRole;
import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecurityProperties.Jwt properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] signingKey;

    public JwtService(SecurityProperties securityProperties, ObjectMapper objectMapper, Clock clock) {
        this.properties = securityProperties.jwt();
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.signingKey = properties.secret().getBytes(StandardCharsets.UTF_8);
    }

    public String issueToken(UserAccount user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(properties.expirationSeconds());

        ObjectNode header = objectMapper.createObjectNode()
                .put("alg", "HS256")
                .put("typ", "JWT");
        ObjectNode payload = objectMapper.createObjectNode()
                .put("iss", properties.issuer())
                .put("sub", user.getUsername())
                .put("role", user.getRole().name())
                .put("iat", issuedAt.getEpochSecond())
                .put("exp", expiresAt.getEpochSecond());

        try {
            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encode(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            return signingInput + "." + encode(sign(signingInput));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue access token", exception);
        }
    }

    public JwtPrincipal validate(String token) {
        try {
            String[] segments = token.split("\\.", -1);
            if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank()) {
                throw new InvalidJwtException();
            }

            String signingInput = segments[0] + "." + segments[1];
            byte[] suppliedSignature = URL_DECODER.decode(segments[2]);
            if (!MessageDigest.isEqual(sign(signingInput), suppliedSignature)) {
                throw new InvalidJwtException();
            }

            JsonNode header = objectMapper.readTree(URL_DECODER.decode(segments[0]));
            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(segments[1]));
            if (!"HS256".equals(requiredText(header, "alg")) || !"JWT".equals(requiredText(header, "typ"))) {
                throw new InvalidJwtException();
            }
            if (!properties.issuer().equals(requiredText(payload, "iss"))) {
                throw new InvalidJwtException();
            }

            String username = requiredText(payload, "sub");
            ApplicationRole role = ApplicationRole.valueOf(requiredText(payload, "role"));
            long issuedAt = requiredLong(payload, "iat");
            long expiresAt = requiredLong(payload, "exp");
            long now = clock.instant().getEpochSecond();
            if (expiresAt <= now || issuedAt > now + 60 || expiresAt <= issuedAt) {
                throw new InvalidJwtException();
            }
            return new JwtPrincipal(username, role);
        } catch (InvalidJwtException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidJwtException();
        }
    }

    public long expirationSeconds() {
        return properties.expirationSeconds();
    }

    private byte[] sign(String signingInput) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new InvalidJwtException();
        }
        return value.textValue();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new InvalidJwtException();
        }
        return value.longValue();
    }
}
