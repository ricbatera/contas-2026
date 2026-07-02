package br.com.rdrtech.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Jwt> getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwtAuth
                ? Optional.of(jwtAuth.getToken())
                : Optional.empty();
    }

    public static Optional<UUID> getCurrentUserId() {
        return getJwt().map(jwt -> UUID.fromString(jwt.getSubject()));
    }
}
