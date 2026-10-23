package com.fraudshield.backend.security;

import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.auth.UserAccountRepository;
import com.fraudshield.backend.common.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final ApiErrorWriter apiErrorWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserAccountRepository userAccountRepository,
            ApiErrorWriter apiErrorWriter) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/v1/health".equals(path)
                || "/api/v1/auth/login".equals(path)
                || ("POST".equals(request.getMethod()) && "/api/v1/authorisations".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() == 7) {
            apiErrorWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "INVALID_TOKEN",
                    "The access token is invalid or expired");
            return;
        }

        try {
            JwtPrincipal principal = jwtService.validate(authorization.substring(7));
            UserAccount currentUser = userAccountRepository.findByUsername(principal.username())
                    .filter(UserAccount::isEnabled)
                    .filter(user -> user.getRole() == principal.role())
                    .orElseThrow(InvalidJwtException::new);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    currentUser.getUsername(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidJwtException exception) {
            SecurityContextHolder.clearContext();
            apiErrorWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "INVALID_TOKEN",
                    "The access token is invalid or expired");
        }
    }
}
