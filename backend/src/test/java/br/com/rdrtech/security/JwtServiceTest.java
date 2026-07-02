package br.com.rdrtech.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.rdrtech.role.NomePerfil;
import br.com.rdrtech.role.Role;
import br.com.rdrtech.usuario.Usuario;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    private JwtService jwtService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(
                "test-secret-key-for-junit-tests-only-32bytes-min".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtProperties properties = new JwtProperties(
                "test-secret-key-for-junit-tests-only-32bytes-min",
                "financas-api-test",
                Duration.ofHours(1),
                Duration.ofDays(30));
        jwtService = new JwtService(
                new NimbusJwtEncoder(new ImmutableSecret<>(secretKey)),
                NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build(),
                properties);

        Role role = Role.builder().nome(NomePerfil.USER).build();
        usuario = Usuario.builder()
                .nome("Rodrigo")
                .sobrenome("Dias")
                .email("rodrigo@example.com")
                .senha("hash")
                .roles(Set.of(role))
                .build();
        usuario.setId(UUID.randomUUID());
    }

    @Test
    void deveGerarEDecodificarAccessTokenComClaimsCorretas() {
        GeneratedToken generated = jwtService.generateAccessToken(usuario);

        Jwt jwt = jwtService.decode(generated.token());

        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getId()).isEqualTo(generated.jti().toString());
        assertThat(jwt.getClaimAsString(JwtService.CLAIM_TYPE)).isEqualTo(JwtService.TYPE_ACCESS);
        assertThat(jwt.getClaimAsString("email")).isEqualTo(usuario.getEmail());
        @SuppressWarnings("unchecked")
        Collection<String> roles = (Collection<String>) jwt.getClaim("roles");
        assertThat(roles).contains("USER");
        // claims JWT "exp" são NumericDate (segundos) — precisão de nanos é perdida no round-trip
        assertThat(jwt.getExpiresAt()).isEqualTo(generated.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void deveGerarRefreshTokenSemClaimRoles() {
        GeneratedToken generated = jwtService.generateRefreshToken(usuario);

        Jwt jwt = jwtService.decode(generated.token());

        assertThat(jwt.getClaimAsString(JwtService.CLAIM_TYPE)).isEqualTo(JwtService.TYPE_REFRESH);
        assertThat((Object) jwt.getClaim("roles")).isNull();
    }

    @Test
    void deveLancarExcecaoParaTokenAdulterado() {
        String tokenValido = jwtService.generateAccessToken(usuario).token();
        String tokenAdulterado = tokenValido.substring(0, tokenValido.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.decode(tokenAdulterado)).isInstanceOf(JwtException.class);
    }
}
