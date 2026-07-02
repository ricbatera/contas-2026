package br.com.rdrtech.security;

import java.time.Instant;
import java.util.UUID;

public record GeneratedToken(String token, UUID jti, Instant expiresAt) {
}
