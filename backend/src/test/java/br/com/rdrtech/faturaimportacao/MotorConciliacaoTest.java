package br.com.rdrtech.faturaimportacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.rdrtech.faturaimportacao.MotorConciliacao.Exata;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Item;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Nova;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.ParIa;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Parcela;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.PorValor;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.PreSaidaExistente;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Reaproveitada;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Resultado;
import br.com.rdrtech.presaida.StatusPreSaida;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MotorConciliacaoTest {

    private static final int COMPETENCIA = 202610;
    private static final UUID CARTAO = UUID.randomUUID();

    @Test
    void deveCasarPelaParcelaEDesempatarPelaDescricao() {
        Parcela cinema = parcela("Cinema", null, "40.00", 2, 3);
        Parcela livraria = parcela("Livraria Cultura", null, "40.00", 2, 3);

        Resultado resultado = conciliar(List.of(item(0, "LIVRARIA CULTURA SP", "40.00", 2, 3)), List.of(cinema, livraria));

        assertThat(resultado.decisoes().get(0)).isEqualTo(new Exata(livraria));
        assertThat(resultado.faltantes()).containsExactly(cinema);
    }

    @Test
    void deveVincularPorValorUnicoQuandoAParcelaNaoTemDescricaoDeFatura() {
        // Assinatura lançada à mão como 12x; na fatura aparece como 1/1.
        Parcela prime = parcela("Amazon Prime", null, "19.90", 8, 12);

        Resultado resultado = conciliar(List.of(item(0, "AMAZONPRIME*BR", "19.90", 1, 1)), List.of(prime));

        assertThat(resultado.decisoes().get(0)).isInstanceOfSatisfying(PorValor.class, porValor -> {
            assertThat(porValor.parcela()).isEqualTo(prime);
            assertThat(porValor.observacao()).contains("1/1 no PDF", "8/12 no sistema");
        });
        assertThat(resultado.faltantes()).isEmpty();
    }

    @Test
    void naoDeveVincularPorValorQuandoADescricaoDeFaturaEDeOutraCompra() {
        Parcela outra = parcela("Farmácia", "DROGASIL 123", "19.90", 1, 1);

        Resultado resultado = conciliar(List.of(item(0, "AMAZONPRIME*BR", "19.90", 1, 1)), List.of(outra));

        assertThat(resultado.decisoes().get(0)).isEqualTo(new Nova(null));
        assertThat(resultado.faltantes()).containsExactly(outra);
    }

    @Test
    void naoDeveEscolherAsCegasEntreSaidasDiferentesDeMesmoValor() {
        // Nada na descrição liga "NFLX" a "Netflix": o vínculo exato seria um chute.
        Parcela netflix = parcela("Netflix", null, "50.00", 1, 1);
        Parcela academia = parcela("Academia", null, "50.00", 1, 1);
        List<Item> itens = List.of(item(0, "NFLX", "50.00", 1, 1), item(1, "PG *SMARTFIT", "50.00", 1, 1));

        Resultado resultado = conciliar(itens, List.of(netflix, academia));

        assertThat(resultado.desempate().necessario()).isTrue();
        assertThat(resultado.desempate().itens()).hasSize(2);
        assertThat(List.of(sugerida(resultado, 0), sugerida(resultado, 1))).containsExactlyInAnyOrder(netflix, academia);
        assertThat(resultado.faltantes()).isEmpty();
    }

    @Test
    void deveCasarQuandoADescricaoDesempataOuAsComprasSaoIdenticas() {
        Parcela netflix = parcela("Netflix", null, "50.00", 1, 1);
        Parcela academia = parcela("Academia", null, "50.00", 1, 1);
        Parcela uber1 = parcela("Uber", null, "25.00", 1, 1);
        Parcela uber2 = parcela("Uber", null, "25.00", 1, 1);
        List<Item> itens = List.of(item(0, "NETFLIX.COM", "50.00", 1, 1), item(1, "SMARTFIT", "50.00", 1, 1),
                item(2, "UBER TRIP", "25.00", 1, 1), item(3, "UBER TRIP", "25.00", 1, 1));

        Resultado resultado = conciliar(itens, List.of(netflix, academia, uber1, uber2));

        assertThat(resultado.decisoes().get(0)).isEqualTo(new Exata(netflix));
        assertThat(resultado.decisoes().get(1)).isEqualTo(new Exata(academia));
        assertThat(resultado.decisoes().get(2)).isInstanceOf(Exata.class);
        assertThat(resultado.decisoes().get(3)).isInstanceOf(Exata.class);
        assertThat(resultado.desempate().necessario()).isFalse();
    }

    @Test
    void deveUsarAIndicacaoDaIaComoSugestao() {
        Parcela netflix = parcela("Netflix", null, "50.00", 1, 1);
        Parcela academia = parcela("Academia", null, "50.00", 1, 1);
        List<Item> itens = List.of(item(0, "PG *SMARTFIT", "50.00", 1, 1), item(1, "NFLX", "50.00", 1, 1));
        List<ParIa> pares = List.of(
                new ParIa(0, academia.parcelaId(), "Smart Fit é academia"),
                new ParIa(1, netflix.parcelaId(), "NFLX é Netflix"));

        Resultado resultado = MotorConciliacao.conciliar(itens, List.of(netflix, academia), List.of(), pares);

        Nova primeira = (Nova) resultado.decisoes().get(0);
        assertThat(primeira.sugestao().parcela()).isEqualTo(academia);
        assertThat(primeira.sugestao().motivo()).contains("IA", "Smart Fit é academia");
        assertThat(sugerida(resultado, 1)).isEqualTo(netflix);
    }

    @Test
    void deveAceitarValorDiferenteSoComoSugestaoDaIaComMotivo() {
        Parcela spotify = parcela("Spotify", null, "21.90", 3, 12);
        List<Item> itens = List.of(item(0, "SPOTIFY", "23.90", 1, 1));

        Resultado semMotivo = MotorConciliacao.conciliar(itens, List.of(spotify), List.of(),
                List.of(new ParIa(0, spotify.parcelaId(), "")));
        Resultado comMotivo = MotorConciliacao.conciliar(itens, List.of(spotify), List.of(),
                List.of(new ParIa(0, spotify.parcelaId(), "Reajuste da assinatura")));

        assertThat(semMotivo.decisoes().get(0)).isEqualTo(new Nova(null));
        assertThat(sugerida(comMotivo, 0)).isEqualTo(spotify);
        assertThat(((Nova) comMotivo.decisoes().get(0)).sugestao().motivo()).contains("valor no sistema: 21.90");
    }

    @Test
    void deveSugerirParcelaDeMesVizinhoComDescricaoDeFaturaVazia() {
        Parcela deslocada = new Parcela(UUID.randomUUID(), UUID.randomUUID(), "Curso de inglês", null, CARTAO, "1111",
                new BigDecimal("300.00"), 2, 6, 202609, true);

        Resultado resultado = conciliar(List.of(item(0, "ESCOLA IDIOMAS 03/06", "300.00", 3, 6)), List.of(deslocada));

        assertThat(sugerida(resultado, 0)).isEqualTo(deslocada);
        assertThat(((Nova) resultado.decisoes().get(0)).sugestao().motivo()).contains("09/2026", "datas da saída divergem");
        // Parcela de mês vizinho nunca vira faltante desta fatura.
        assertThat(resultado.faltantes()).isEmpty();
    }

    @Test
    void deveReaproveitarPreSaidaDeOutraImportacaoEConsumirAParcelaDaVinculada() {
        UUID saidaVinculada = UUID.randomUUID();
        Parcela prime = new Parcela(UUID.randomUUID(), saidaVinculada, "Amazon Prime", "AMAZONPRIME*BR", CARTAO, "1111",
                new BigDecimal("19.90"), 8, 12, COMPETENCIA, false);
        PreSaidaExistente vinculada = new PreSaidaExistente(UUID.randomUUID(), StatusPreSaida.VINCULADA, saidaVinculada,
                "AMAZONPRIME*BR", "AMAZONPRIME*BR", new BigDecimal("19.90"), 1, 1, true);
        PreSaidaExistente pendente = new PreSaidaExistente(UUID.randomUUID(), StatusPreSaida.PENDENTE, null,
                "NOTEBOOK", "NOTEBOOK", new BigDecimal("250.00"), 4, 12, false);
        List<Item> itens = List.of(item(0, "AMAZONPRIME*BR", "19.90", 1, 1), item(1, "NOTEBOOK", "250.00", 4, 12));

        Resultado resultado = MotorConciliacao.conciliar(itens, List.of(prime), List.of(vinculada, pendente), null);

        assertThat(resultado.decisoes().get(0)).isEqualTo(new Reaproveitada(vinculada));
        assertThat(resultado.decisoes().get(1)).isEqualTo(new Reaproveitada(pendente));
        assertThat(resultado.faltantes()).isEmpty();
        assertThat(resultado.desempate().necessario()).isFalse();
    }

    @Test
    void sugestaoRecusadaPeloUsuarioDevePrevalecerSobreOCasamentoExato() {
        // O usuário disse que "PG *SMARTFIT" não é a "Academia": mesmo sendo a única parcela de
        // R$ 50, o reprocessamento mantém a pré-saída e a Academia segue como faltante.
        Parcela academia = parcela("Academia", null, "50.00", 1, 1);
        PreSaidaExistente recusada = new PreSaidaExistente(UUID.randomUUID(), StatusPreSaida.PENDENTE, null,
                "PG *SMARTFIT", "PG *SMARTFIT", new BigDecimal("50.00"), 1, 1, true);

        Resultado resultado = MotorConciliacao.conciliar(List.of(item(0, "PG *SMARTFIT", "50.00", 1, 1)),
                List.of(academia), List.of(recusada), null);

        assertThat(resultado.decisoes().get(0)).isEqualTo(new Reaproveitada(recusada));
        assertThat(resultado.faltantes()).containsExactly(academia);
    }

    @Test
    void auxiliaresDevemNormalizarEntradasDaIa() {
        assertThat(MotorConciliacao.normalizarDigitos("**** 1234")).isEqualTo("1234");
        assertThat(MotorConciliacao.normalizarDigitos("12")).isNull();
        assertThat(MotorConciliacao.converterData("2026-10-05T00:00:00")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(MotorConciliacao.converterData("05/10")).isNull();
        assertThat(MotorConciliacao.similaridade("Açaí do João", "ACAI DO JOAO")).isEqualTo(1.0);
        assertThat(MotorConciliacao.primeiroVencimento(LocalDate.of(2026, 3, 31), 2)).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    private static Resultado conciliar(List<Item> itens, List<Parcela> parcelas) {
        return MotorConciliacao.conciliar(itens, parcelas, List.of(), null);
    }

    private static Parcela sugerida(Resultado resultado, int indice) {
        Nova nova = (Nova) resultado.decisoes().get(indice);
        return nova.sugestao() == null ? null : nova.sugestao().parcela();
    }

    private static Item item(int indice, String descricao, String valor, int numero, int quantidade) {
        return new Item(indice, descricao, "1111", CARTAO, new BigDecimal(valor), numero, quantidade);
    }

    private static Parcela parcela(String descricao, String descricaoFatura, String valor, int numero, int quantidade) {
        return new Parcela(UUID.randomUUID(), UUID.randomUUID(), descricao, descricaoFatura, CARTAO, "1111",
                new BigDecimal(valor), numero, quantidade, COMPETENCIA, false);
    }
}
