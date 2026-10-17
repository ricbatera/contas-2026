package br.com.rdrtech.faturaimportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.cartao.CartaoRepository;
import br.com.rdrtech.cartao.TipoCartao;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.exception.FaturaImportacaoInvalidaException;
import br.com.rdrtech.fatura.Fatura;
import br.com.rdrtech.fatura.FaturaRepository;
import br.com.rdrtech.faturaimportacao.FaturaLidaIa.CartaoLido;
import br.com.rdrtech.faturaimportacao.FaturaLidaIa.ItemLido;
import br.com.rdrtech.faturaimportacao.dto.ResumoImportacao;
import br.com.rdrtech.presaida.PreSaida;
import br.com.rdrtech.presaida.PreSaidaRepository;
import br.com.rdrtech.saida.Saida;
import br.com.rdrtech.saida.SaidaParcela;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConciliacaoFaturaServiceTest {

    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 10, 10);

    @Mock
    private CartaoRepository cartaoRepository;
    @Mock
    private FaturaRepository faturaRepository;
    @Mock
    private PreSaidaRepository preSaidaRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @InjectMocks
    private ConciliacaoFaturaService service;

    private Cartao principal;
    private Fatura fatura;

    @BeforeEach
    void setUp() {
        principal = Cartao.builder().nome("Principal").ultimosDigitos("1111").tipo(TipoCartao.PRINCIPAL).build();
        principal.setId(UUID.randomUUID());
        fatura = Fatura.builder().cartaoPrincipal(principal).competencia(202610).vencimento(VENCIMENTO).build();
        when(cartaoRepository.listarPorUltimosDigitos(anyCollection())).thenReturn(List.of(principal));
        when(faturaRepository.buscarPorCartaoECompetencia(principal.getId(), 202610)).thenReturn(Optional.of(fatura));
        when(preSaidaRepository.listarNaoAprovadasDaFatura(principal.getId(), 202610)).thenReturn(List.of());
        when(categoriaRepository.listarComSubcategorias()).thenReturn(List.of());
    }

    @Test
    void deveCasarPelaParcelaEDesempatarPelaDescricao() {
        Saida cinema = saidaComParcela("Cinema", 2, 3, "40.00");
        Saida livraria = saidaComParcela("Livraria Cultura", 2, 3, "40.00");
        saidaComParcela("Livraria antiga", 1, 3, "40.00");

        ResumoImportacao resumo = service.conciliar(new FaturaImportacao(),
                fatura(item("LIVRARIA CULTURA SP", "40.00", 2, 3, "COMPRA")));

        assertThat(resumo.conciliados()).hasSize(1);
        assertThat(resumo.conciliados().getFirst().descricaoSaida()).isEqualTo("Livraria Cultura");
        assertThat(livraria.getDescricaoFatura()).isEqualTo("LIVRARIA CULTURA SP");
        assertThat(cinema.getDescricaoFatura()).isNull();
        verify(preSaidaRepository, never()).save(any());
        // As parcelas que sobraram na fatura do sistema viram aviso.
        assertThat(resumo.avisos()).hasSize(2);
    }

    @Test
    void deveCriarPreSaidaRecuandoAtePrimeiraParcela() {
        ResumoImportacao resumo = service.conciliar(new FaturaImportacao(),
                fatura(item("NOTEBOOK 04/12", "250.00", 4, 12, "COMPRA")));

        ArgumentCaptor<PreSaida> captor = ArgumentCaptor.forClass(PreSaida.class);
        verify(preSaidaRepository).save(captor.capture());
        PreSaida preSaida = captor.getValue();
        assertThat(resumo.preSaidasCriadas()).isEqualTo(1);
        assertThat(preSaida.getPrimeiroVencimento()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(preSaida.getCartao()).isSameAs(principal);
        assertThat(preSaida.getQuantidadeParcelas()).isEqualTo(12);
        assertThat(preSaida.getCompetencia()).isEqualTo(202610);
    }

    @Test
    void deveIgnorarPagamentoEstornoEParcelamentoInvalido() {
        ResumoImportacao resumo = service.conciliar(new FaturaImportacao(), fatura(
                item("PAGAMENTO", "500.00", 1, 1, "PAGAMENTO"),
                item("ESTORNO LOJA", "10.00", 1, 1, "ESTORNO"),
                item("CREDITO", "-5.00", 1, 1, "COMPRA"),
                item("ERRADO", "10.00", 5, 3, "COMPRA")));

        assertThat(resumo.ignorados()).hasSize(4);
        verify(preSaidaRepository, never()).save(any());
    }

    @Test
    void naoDeveDuplicarPreSaidaPendenteDeOutraImportacao() {
        PreSaida pendente = PreSaida.builder().descricao("NOTEBOOK").descricaoFatura("NOTEBOOK")
                .valorPorParcela(new BigDecimal("250.00")).numeroParcela(4).quantidadeParcelas(12).build();
        when(preSaidaRepository.listarNaoAprovadasDaFatura(principal.getId(), 202610)).thenReturn(List.of(pendente));
        FaturaImportacao importacao = new FaturaImportacao();

        ResumoImportacao resumo = service.conciliar(importacao, fatura(item("NOTEBOOK", "250.00", 4, 12, "COMPRA")));

        assertThat(resumo.preSaidasCriadas()).isZero();
        assertThat(pendente.getImportacao()).isSameAs(importacao);
        verify(preSaidaRepository, never()).save(any());
    }

    @Test
    void deveFalharQuandoNenhumCartaoCorresponde() {
        when(cartaoRepository.listarPorUltimosDigitos(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.conciliar(new FaturaImportacao(), fatura(item("X", "1.00", 1, 1, "COMPRA"))))
                .isInstanceOf(FaturaImportacaoInvalidaException.class)
                .hasMessageContaining("1111");
    }

    @Test
    void auxiliaresDevemNormalizarEntradasDaIa() {
        assertThat(ConciliacaoFaturaService.normalizarDigitos("**** 1234")).isEqualTo("1234");
        assertThat(ConciliacaoFaturaService.normalizarDigitos("12")).isNull();
        assertThat(ConciliacaoFaturaService.converterData("2026-10-05T00:00:00")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ConciliacaoFaturaService.converterData("05/10")).isNull();
        assertThat(ConciliacaoFaturaService.similaridade("Açaí do João", "ACAI DO JOAO")).isEqualTo(1.0);
        assertThat(ConciliacaoFaturaService.primeiroVencimento(LocalDate.of(2026, 3, 31), 2))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    private Saida saidaComParcela(String descricao, int numero, int quantidade, String valor) {
        Saida saida = Saida.builder().descricao(descricao).quantidadeParcelas(quantidade)
                .valorPorParcela(new BigDecimal(valor)).cartao(principal).build();
        saida.setId(UUID.randomUUID());
        SaidaParcela parcela = SaidaParcela.builder().saida(saida).numero(numero).valor(new BigDecimal(valor))
                .vencimento(VENCIMENTO).build();
        fatura.adicionarParcela(parcela);
        return saida;
    }

    private static FaturaLidaIa fatura(ItemLido... itens) {
        return new FaturaLidaIa(VENCIMENTO.toString(), null, List.of(new CartaoLido("1111", null)), List.of(itens));
    }

    private static ItemLido item(String descricao, String valor, int parcela, int total, String tipo) {
        return new ItemLido("1111", null, descricao, new BigDecimal(valor), parcela, total, tipo, null, null);
    }
}
