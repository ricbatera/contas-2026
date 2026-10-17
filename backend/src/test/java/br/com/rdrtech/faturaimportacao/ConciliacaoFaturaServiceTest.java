package br.com.rdrtech.faturaimportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
    private Fatura faturaAnterior;
    private FaturaImportacao importacao;

    @BeforeEach
    void setUp() {
        principal = Cartao.builder().nome("Principal").ultimosDigitos("1111").tipo(TipoCartao.PRINCIPAL).build();
        principal.setId(UUID.randomUUID());
        fatura = Fatura.builder().cartaoPrincipal(principal).competencia(202610).vencimento(VENCIMENTO).build();
        faturaAnterior = Fatura.builder().cartaoPrincipal(principal).competencia(202609)
                .vencimento(VENCIMENTO.minusMonths(1)).build();
        importacao = new FaturaImportacao();
        importacao.setId(UUID.randomUUID());
        when(cartaoRepository.listarPorUltimosDigitos(anyCollection())).thenReturn(List.of(principal));
        when(faturaRepository.buscarPorCartaoECompetencia(eq(principal.getId()), anyInt())).thenReturn(Optional.empty());
        when(faturaRepository.buscarPorCartaoECompetencia(principal.getId(), 202610)).thenReturn(Optional.of(fatura));
        when(faturaRepository.buscarPorCartaoECompetencia(principal.getId(), 202609))
                .thenReturn(Optional.of(faturaAnterior));
        when(preSaidaRepository.listarReaproveitaveis(any(), anyInt(), any())).thenReturn(List.of());
        when(categoriaRepository.listarComSubcategorias()).thenReturn(List.of());
    }

    @Test
    void devePreencherDescricaoDaFaturaDaAssinaturaLancadaComOutraNumeracao() {
        Saida prime = saidaComParcela(fatura, "Amazon Prime", 8, 12, "19.90");

        ResumoImportacao resumo = service.aplicar(importacao, fatura(item("AMAZONPRIME*BR", "19.90", 1, 1)),
                List.of(), List.of());

        assertThat(prime.getDescricaoFatura()).isEqualTo("AMAZONPRIME*BR");
        assertThat(resumo.conciliados()).singleElement().satisfies(item -> {
            assertThat(item.criterio()).isEqualTo("VALOR");
            assertThat(item.observacao()).contains("8/12");
        });
        assertThat(resumo.avisos()).isEmpty();
        assertThat(importacao.getCompetencia()).isEqualTo(202610);
        verify(preSaidaRepository, never()).save(any());
    }

    @Test
    void deveCriarPreSaidaComSugestaoDeSaidaLancadaNoMesAnterior() {
        Saida curso = saidaComParcela(faturaAnterior, "Curso", 2, 6, "300.00");

        ResumoImportacao resumo = service.aplicar(importacao, fatura(item("ESCOLA IDIOMAS", "300.00", 3, 6)),
                List.of(), List.of("aviso do desempate"));

        ArgumentCaptor<PreSaida> captor = ArgumentCaptor.forClass(PreSaida.class);
        verify(preSaidaRepository).save(captor.capture());
        assertThat(captor.getValue().getSaidaSugerida()).isSameAs(curso);
        assertThat(captor.getValue().getMotivoSugestao()).contains("09/2026");
        assertThat(captor.getValue().getPrimeiroVencimento()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(resumo.preSaidasCriadas()).isEqualTo(1);
        assertThat(resumo.avisos()).containsExactly("aviso do desempate");
    }

    @Test
    void planejarDeveIndicarDesempateQuandoHaValoresRepetidos() {
        saidaComParcela(fatura, "Netflix", 1, 1, "50.00");
        saidaComParcela(fatura, "Academia", 1, 1, "50.00");

        var desempate = service.planejar(importacao.getId(),
                fatura(item("NFLX", "50.00", 1, 1), item("SMARTFIT", "50.00", 1, 1)));

        assertThat(desempate.necessario()).isTrue();
        assertThat(desempate.itens()).hasSize(2);
        assertThat(desempate.parcelas()).hasSize(2);
    }

    @Test
    void deveFalharQuandoNenhumCartaoCorresponde() {
        when(cartaoRepository.listarPorUltimosDigitos(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.aplicar(importacao, fatura(item("X", "1.00", 1, 1)), List.of(), List.of()))
                .isInstanceOf(FaturaImportacaoInvalidaException.class)
                .hasMessageContaining("1111");
    }

    private Saida saidaComParcela(Fatura destino, String descricao, int numero, int quantidade, String valor) {
        Saida saida = Saida.builder().descricao(descricao).quantidadeParcelas(quantidade)
                .valorPorParcela(new BigDecimal(valor)).cartao(principal).build();
        saida.setId(UUID.randomUUID());
        SaidaParcela parcela = SaidaParcela.builder().saida(saida).numero(numero).valor(new BigDecimal(valor))
                .vencimento(destino.getVencimento()).build();
        parcela.setId(UUID.randomUUID());
        destino.adicionarParcela(parcela);
        return saida;
    }

    private static FaturaLidaIa fatura(ItemLido... itens) {
        return new FaturaLidaIa(VENCIMENTO.toString(), null, List.of(new CartaoLido("1111", null)), List.of(itens));
    }

    private static ItemLido item(String descricao, String valor, int parcela, int total) {
        return new ItemLido("1111", null, descricao, new BigDecimal(valor), parcela, total, "COMPRA", null, null);
    }
}
