package br.com.rdrtech.auth;

import br.com.rdrtech.exception.LoginBloqueadoException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Conta as senhas erradas por e-mail e bloqueia o login temporariamente. E-mails que não existem
 * são contados do mesmo jeito, para a resposta não revelar quais contas estão cadastradas.
 *
 * <p>O estado fica em memória (backend em instância única): fora da transação do login, que é
 * desfeita quando a senha está errada, e sem tabela nova. Um reinício zera os contadores. Cada
 * entrada expira depois da duração do bloqueio sem novas falhas.
 */
@Service
public class BloqueioLoginService {

    private final BloqueioLoginProperties properties;
    private final Clock clock;
    private final Cache<String, Tentativas> tentativas;

    @Autowired
    public BloqueioLoginService(BloqueioLoginProperties properties) {
        this(properties, Clock.systemUTC());
    }

    BloqueioLoginService(BloqueioLoginProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.tentativas = Caffeine.newBuilder()
                .expireAfterWrite(properties.duracaoBloqueio())
                .ticker(() -> clock.millis() * 1_000_000L)
                .maximumSize(100_000)
                .build();
    }

    public void verificar(String email) {
        Tentativas atual = tentativas.getIfPresent(chave(email));
        Instant agora = clock.instant();
        if (atual != null && atual.bloqueadoAte() != null && agora.isBefore(atual.bloqueadoAte())) {
            long minutos = Math.max(1, (Duration.between(agora, atual.bloqueadoAte()).toSeconds() + 59) / 60);
            throw new LoginBloqueadoException(minutos);
        }
    }

    public void registrarFalha(String email) {
        tentativas.asMap().compute(chave(email), (k, atual) -> {
            int falhas = (atual == null || atual.bloqueadoAte() != null ? 0 : atual.falhas()) + 1;
            Instant bloqueadoAte = falhas >= properties.maxTentativas()
                    ? clock.instant().plus(properties.duracaoBloqueio())
                    : null;
            return new Tentativas(falhas, bloqueadoAte);
        });
    }

    public void registrarSucesso(String email) {
        tentativas.invalidate(chave(email));
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Tentativas(int falhas, Instant bloqueadoAte) {
    }
}
