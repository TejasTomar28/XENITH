package com.fraudshield.backend.security;

import com.fraudshield.backend.common.exception.ApiErrorWriter;
import com.fraudshield.backend.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final byte[] expectedKey;
    private final ApiErrorWriter apiErrorWriter;

    public GatewayApiKeyFilter(SecurityProperties securityProperties, ApiErrorWriter apiErrorWriter) {
        this.expectedKey = securityProperties.gateway().apiKey().getBytes(StandardCharsets.UTF_8);
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/v1/authorisations".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String suppliedKey = request.getHeader(API_KEY_HEADER);
        boolean valid = suppliedKey != null
                && MessageDigest.isEqual(expectedKey, suppliedKey.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            SecurityContextHolder.clearContext();
            apiErrorWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "INVALID_GATEWAY_API_KEY",
                    "Gateway authentication failed");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "transaction-gateway",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_GATEWAY")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
