package br.com.rdrtech.security;

import br.com.rdrtech.usuario.Usuario;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    // JwtEncoderParameters.from(claims) usa RS256 por padrão, incompatível com a chave HMAC
    // usada por este serviço — o header precisa declarar HS256 explicitamente.
    private static final JwsHeader JWS_HEADER = JwsHeader.with(MacAlgorithm.HS256).build();

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;

    public GeneratedToken generateAccessToken(Usuario usuario) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.accessTokenExpiration());
        UUID jti = UUID.randomUUID();

        Set<String> roles = usuario.getRoles().stream()
                .map(role -> role.getNome().name())
                .collect(Collectors.toSet());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(usuario.getId().toString())
                .id(jti.toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim("email", usuario.getEmail())
                .claim("nome", usuario.getNome())
                .claim("sobrenome", usuario.getSobrenome())
                .claim("roles", roles)
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(JWS_HEADER, claims)).getTokenValue();
        return new GeneratedToken(token, jti, expiresAt);
    }

    public GeneratedToken generateRefreshToken(Usuario usuario) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenExpiration());
        UUID jti = UUID.randomUUID();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(usuario.getId().toString())
                .id(jti.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(JWS_HEADER, claims)).getTokenValue();
        return new GeneratedToken(token, jti, expiresAt);
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    public long accessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpiration().toSeconds();
    }
}
