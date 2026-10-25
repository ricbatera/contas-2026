package br.com.rdrtech.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limite de requisições por IP no backend. {@code auth} vale para {@code /api/auth/**} (login,
 * cadastro, refresh) e {@code api} para o restante de {@code /api/**}. Os padrões ficam aqui porque
 * o application.properties dos testes substitui o principal.
 */
@ConfigurationProperties(prefix = "seguranca.rate-limit")
public record RateLimitProperties(Boolean habilitado, Limite auth, Limite api) {

    public RateLimitProperties {
        habilitado = habilitado == null || habilitado;
        auth = auth == null ? new Limite(10L, Duration.ofMinutes(1)) : auth;
        api = api == null ? new Limite(200L, Duration.ofMinutes(1)) : api;
    }

    /** Até {@code capacidade} requisições por {@code janela}, reabastecidas aos poucos. */
    public record Limite(Long capacidade, Duration janela) {

        public Limite {
            if (capacidade == null || capacidade < 1 || janela == null || janela.isZero() || janela.isNegative()) {
                throw new IllegalStateException("seguranca.rate-limit: capacidade e janela devem ser positivas");
            }
        }
    }
}
