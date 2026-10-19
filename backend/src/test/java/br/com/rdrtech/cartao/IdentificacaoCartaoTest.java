package br.com.rdrtech.cartao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentificacaoCartaoTest {

    @Test
    void deveNormalizarDigitosMantendoOsQuatroUltimosOuTresVisiveis() {
        assertThat(IdentificacaoCartao.normalizarDigitos("************1234")).isEqualTo("1234");
        assertThat(IdentificacaoCartao.normalizarDigitos("*117")).isEqualTo("117");
        assertThat(IdentificacaoCartao.normalizarDigitos("12")).isNull();
        assertThat(IdentificacaoCartao.normalizarDigitos(null)).isNull();
    }

    @Test
    void deveEscolherCartaoPeloFinalPorSufixo() {
        Cartao cartao = cartao("4117", Bandeira.VISA, true);
        List<Cartao> cartoes = List.of(cartao, cartao("9999", Bandeira.VISA, true));

        assertThat(IdentificacaoCartao.escolher(cartoes, "XXXX 4117", null)).contains(cartao);
        assertThat(IdentificacaoCartao.escolher(cartoes, "*117", null)).contains(cartao);
        assertThat(IdentificacaoCartao.escolher(cartoes, "5555", null)).isEmpty();
        assertThat(IdentificacaoCartao.escolher(cartoes, null, Bandeira.VISA)).isEmpty();
    }

    @Test
    void devePreferirAtivoEDesempatarPelaBandeira() {
        Cartao inativo = cartao("1234", Bandeira.VISA, false);
        Cartao ativo = cartao("1234", Bandeira.VISA, true);
        assertThat(IdentificacaoCartao.escolher(List.of(inativo, ativo), "1234", null)).contains(ativo);

        Cartao visa = cartao("5678", Bandeira.VISA, true);
        Cartao elo = cartao("5678", Bandeira.ELO, true);
        assertThat(IdentificacaoCartao.escolher(List.of(visa, elo), "5678", Bandeira.ELO)).contains(elo);
        assertThat(IdentificacaoCartao.escolher(List.of(visa, elo), "5678", null)).isEmpty();
        assertThat(IdentificacaoCartao.escolher(List.of(visa, elo), "5678", Bandeira.AMEX)).isEmpty();
    }

    @Test
    void deveCalcularVencimentoAntesEDepoisDoFechamento() {
        // Fecha dia 15, vence dia 25 do mesmo mês.
        assertThat(IdentificacaoCartao.primeiroVencimento(LocalDate.of(2026, 3, 14), 15, 25))
                .isEqualTo(LocalDate.of(2026, 3, 25));
        assertThat(IdentificacaoCartao.primeiroVencimento(LocalDate.of(2026, 3, 15), 15, 25))
                .isEqualTo(LocalDate.of(2026, 4, 25));
        // Fecha dia 28, vence dia 5 do mês seguinte, inclusive na virada do ano.
        assertThat(IdentificacaoCartao.primeiroVencimento(LocalDate.of(2026, 12, 10), 28, 5))
                .isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(IdentificacaoCartao.primeiroVencimento(LocalDate.of(2026, 12, 29), 28, 5))
                .isEqualTo(LocalDate.of(2027, 2, 5));
    }

    @Test
    void deveAjustarDiasAlemDoFimDoMes() {
        // Fechamento no dia 30: em fevereiro, fecha no último dia.
        assertThat(IdentificacaoCartao.primeiroVencimento(LocalDate.of(2026, 2, 27), 30, 31))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(IdentificacaoCartao.primeiroVencimento(LocalDate.of(2026, 2, 28), 30, 31))
                .isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(IdentificacaoCartao.vencimentoNoMesSeguinte(LocalDate.of(2026, 1, 20), 31))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    private static Cartao cartao(String digitos, Bandeira bandeira, boolean ativo) {
        Cartao cartao = Cartao.builder().nome("Cartão " + digitos).banco("Banco").bandeira(bandeira)
                .ultimosDigitos(digitos).ativo(ativo).build();
        cartao.setId(UUID.randomUUID());
        return cartao;
    }
}
