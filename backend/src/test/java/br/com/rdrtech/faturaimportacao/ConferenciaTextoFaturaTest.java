package br.com.rdrtech.faturaimportacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.rdrtech.faturaimportacao.FaturaLidaIa.ItemLido;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConferenciaTextoFaturaTest {

    /** Trecho no formato de uma fatura real: marcador antes da data e parcela depois da descrição. */
    private static final String PAGINA = """
            FULANO DE TAL -  5228 XXXX XXXX 5464
            Pagamento e Demais Créditos
            Compra Data Descrição Parcela R$ US$
             08/09 DEB  AUTOM  DE FATURA EM C/ -8.808,74
            Parcelamentos
            Compra Data Descrição Parcela R$ US$
            2 06/12 JIM COM* LOJA EXEMPLO 10/12 255,00
            3 05/03 LJ 13  CALCADOS EXEMPLO 07/10 292,23
            1 22/04 MAGALU FL 989 06/06 92,02
            1 22/04 MAGALU FL 989 06/10 395,52
            Despesas
            Compra Data Descrição Parcela R$ US$
            3 30/08 DT PETRO 100,00
            3 16/09 CAMISARIA EXEMPLO 2.074,52
             15/09 PAYPAL *GITHUB INC 58,35 10,55
            IOF DESPESA NO EXTERIOR 2,04
            VALOR TOTAL 9.139,96 0,00
            Saldo Anterior 8.808,76
            """;

    @Test
    void deveContarSoAsLinhasDeLancamento() {
        // Pagamento, 4 parcelamentos e 3 despesas com data; IOF sem data, total e saldo não contam.
        assertThat(ConferenciaTextoFatura.contarLancamentos(PAGINA)).isEqualTo(8);
        assertThat(ConferenciaTextoFatura.temLancamentos(PAGINA)).isTrue();
        assertThat(ConferenciaTextoFatura.temLancamentos("Juros e CET para o próximo período\nTaxa 9,99% a.m.")).isFalse();
    }

    @Test
    void deveDividirEmTrechosComLimiteDeLancamentosSemPerderLinhas() {
        List<String> trechos = ConferenciaTextoFatura.dividir(PAGINA, 3);

        // 8 lançamentos em trechos de até 3; cabeçalhos ficam no trecho em que aparecem.
        assertThat(trechos).hasSize(3);
        assertThat(trechos).extracting(ConferenciaTextoFatura::contarLancamentos).containsExactly(3, 3, 2);
        assertThat(trechos.getFirst()).startsWith("FULANO DE TAL");
        assertThat(String.join("", trechos)).isEqualTo(PAGINA);
        assertThat(ConferenciaTextoFatura.dividir("sem lançamentos", 3)).containsExactly("sem lançamentos\n");
    }

    @Test
    void naoDeveConfundirMarcadorAntesDaDataComParcela() {
        List<ItemLido> corrigidos = ConferenciaTextoFatura.corrigirParcelas(List.of(
                item("DT PETRO", "100.00", 3, 3),
                item("CAMISARIA EXEMPLO", "2074.52", 3, 3)), PAGINA);

        assertThat(corrigidos).extracting(ItemLido::parcelaAtual, ItemLido::totalParcelas)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, 1), org.assertj.core.groups.Tuple.tuple(1, 1));
    }

    @Test
    void deveLerAParcelaDaLinhaDoItemPeloValor() {
        // A IA errou as parcelas; as duas compras MAGALU têm a mesma descrição e se distinguem pelo valor.
        List<ItemLido> corrigidos = ConferenciaTextoFatura.corrigirParcelas(List.of(
                item("MAGALU FL 989", "92.02", 1, 1),
                item("MAGALU FL 989", "395.52", 2, 2),
                item("LJ 13 CALCADOS EXEMPLO", "292.23", 3, 3),
                item("JIM COM* LOJA EXEMPLO", "255.00", 10, 12)), PAGINA);

        assertThat(corrigidos).extracting(ItemLido::parcelaAtual, ItemLido::totalParcelas).containsExactly(
                org.assertj.core.groups.Tuple.tuple(6, 6),
                org.assertj.core.groups.Tuple.tuple(6, 10),
                org.assertj.core.groups.Tuple.tuple(7, 10),
                org.assertj.core.groups.Tuple.tuple(10, 12));
    }

    @Test
    void deveManterOItemQuandoADescricaoNaoEstaNoTexto() {
        ItemLido item = item("DESCRICAO REESCRITA PELA IA", "10.00", 2, 5);

        assertThat(ConferenciaTextoFatura.corrigirParcelas(List.of(item), PAGINA)).containsExactly(item);
    }

    private static ItemLido item(String descricao, String valor, int parcela, int total) {
        return new ItemLido("5464", null, descricao, new BigDecimal(valor), parcela, total, "COMPRA", null, null);
    }
}
