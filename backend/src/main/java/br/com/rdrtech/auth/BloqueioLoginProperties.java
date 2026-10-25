package br.com.rdrtech.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bloqueio temporário do login: depois de {@code maxTentativas} senhas erradas seguidas para o
 * mesmo e-mail, o login fica bloqueado por {@code duracaoBloqueio}.
 */
@ConfigurationProperties(prefix = "seguranca.login")
public record BloqueioLoginProperties(Integer maxTentativas, Duration duracaoBloqueio) {

    public BloqueioLoginProperties {
        maxTentativas = maxTentativas == null ? 5 : maxTentativas;
        duracaoBloqueio = duracaoBloqueio == null ? Duration.ofMinutes(15) : duracaoBloqueio;
        if (maxTentativas < 1 || duracaoBloqueio.isZero() || duracaoBloqueio.isNegative()) {
            throw new IllegalStateException("seguranca.login: max-tentativas e duracao-bloqueio devem ser positivos");
        }
    }
}
