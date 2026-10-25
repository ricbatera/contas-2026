package br.com.rdrtech.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.rdrtech.exception.LoginBloqueadoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BloqueioLoginServiceTest {

    private static final String EMAIL = "fulano@example.com";

    private RelogioAjustavel relogio;
    private BloqueioLoginService service;

    @BeforeEach
    void setUp() {
        relogio = new RelogioAjustavel(Instant.parse("2026-10-25T12:00:00Z"));
        service = new BloqueioLoginService(new BloqueioLoginProperties(5, Duration.ofMinutes(15)), relogio);
    }

    @Test
    void deveBloquearNaQuintaFalha() {
        for (int i = 0; i < 4; i++) {
            service.registrarFalha(EMAIL);
        }
        assertThatCode(() -> service.verificar(EMAIL)).doesNotThrowAnyException();

        service.registrarFalha(EMAIL);

        assertThatThrownBy(() -> service.verificar(EMAIL))
                .isInstanceOf(LoginBloqueadoException.class)
                .hasMessage("Muitas tentativas de login. Tente novamente em 15 minutos.");
    }

    @Test
    void deveLiberarDepoisDaDuracaoDoBloqueio() {
        bloquear(EMAIL);

        relogio.avancar(Duration.ofMinutes(14).plusSeconds(30));
        assertThatThrownBy(() -> service.verificar(EMAIL)).hasMessageContaining("1 minuto.");

        relogio.avancar(Duration.ofSeconds(31));
        assertThatCode(() -> service.verificar(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void sucessoDeveZerarAsFalhas() {
        for (int i = 0; i < 4; i++) {
            service.registrarFalha(EMAIL);
        }
        service.registrarSucesso(EMAIL);
        service.registrarFalha(EMAIL);

        assertThatCode(() -> service.verificar(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void deveIgnorarCaixaEEspacosDoEmail() {
        bloquear(" Fulano@Example.COM ");

        assertThatThrownBy(() -> service.verificar(EMAIL)).isInstanceOf(LoginBloqueadoException.class);
    }

    @Test
    void naoDeveAfetarOutroEmail() {
        bloquear(EMAIL);

        assertThatCode(() -> service.verificar("outro@example.com")).doesNotThrowAnyException();
    }

    private void bloquear(String email) {
        for (int i = 0; i < 5; i++) {
            service.registrarFalha(email);
        }
    }

    private static final class RelogioAjustavel extends Clock {

        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
