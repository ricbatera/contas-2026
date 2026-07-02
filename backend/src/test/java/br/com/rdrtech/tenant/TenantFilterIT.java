package br.com.rdrtech.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Spike dedicado a provar que o mecanismo @FilterDef(autoEnabled = true) + resolver funciona
 * de ponta a ponta (auto-preenchimento no insert via @PrePersist + filtragem automática via
 * Hibernate) ANTES de qualquer entidade de negócio real (Cartão, Lançamento, etc.) depender
 * dele. Sem @Transactional no método de teste: cada chamada de repositório abre sua própria
 * transação/sessão, e o resolver ({@link CurrentTenantParamResolver}) é reavaliado a cada
 * query a partir do SecurityContext corrente — simula o que acontece de fato em requisições
 * HTTP reais, uma por thread.
 */
@SpringBootTest
class TenantFilterIT {

    @Autowired
    private TenantTestEntityRepository repository;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void devePreencherTenantIdAutomaticamenteEIsolarPorUsuario() {
        UUID usuarioA = UUID.randomUUID();
        UUID usuarioB = UUID.randomUUID();

        autenticarComo(usuarioA);
        TenantTestEntity entidadeA = new TenantTestEntity();
        entidadeA.setDescricao("dado do usuario A");
        entidadeA = repository.saveAndFlush(entidadeA);
        assertThat(entidadeA.getTenantId()).isEqualTo(usuarioA);

        autenticarComo(usuarioB);
        TenantTestEntity entidadeB = new TenantTestEntity();
        entidadeB.setDescricao("dado do usuario B");
        entidadeB = repository.saveAndFlush(entidadeB);
        assertThat(entidadeB.getTenantId()).isEqualTo(usuarioB);

        autenticarComo(usuarioA);
        List<TenantTestEntity> visiveisParaA = repository.findAll();
        assertThat(visiveisParaA)
                .extracting(TenantTestEntity::getDescricao)
                .containsExactly("dado do usuario A");

        autenticarComo(usuarioB);
        List<TenantTestEntity> visiveisParaB = repository.findAll();
        assertThat(visiveisParaB)
                .extracting(TenantTestEntity::getDescricao)
                .containsExactly("dado do usuario B");
    }

    @Test
    void deveFalharAoPersistirSemUsuarioAutenticado() {
        SecurityContextHolder.clearContext();
        TenantTestEntity entidade = new TenantTestEntity();
        entidade.setDescricao("sem tenant");

        assertThatThrownBy(() -> repository.saveAndFlush(entidade))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    private void autenticarComo(UUID usuarioId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(usuarioId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }
}
