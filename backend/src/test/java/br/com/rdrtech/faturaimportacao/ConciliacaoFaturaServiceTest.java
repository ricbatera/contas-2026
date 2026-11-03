package br.com.rdrtech.faturaimportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        when(cartaoRepository.listarComPrincipal()).thenReturn(List.of(principal));
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
        when(cartaoRepository.listarComPrincipal()).thenReturn(List.of());

        assertThatThrownBy(() -> service.aplicar(importacao, fatura(item("X", "1.00", 1, 1)), List.of(), List.of()))
                .isInstanceOf(FaturaImportacaoInvalidaException.class)
                .hasMessageContaining("1111");
    }

    @Test
    void deveIdentificarCartaoPeloFinalMascaradoNaFatura() {
        Cartao outro = Cartao.builder().nome("Outro").ultimosDigitos("2222").tipo(TipoCartao.PRINCIPAL).build();
        outro.setId(UUID.randomUUID());
        when(cartaoRepository.listarComPrincipal()).thenReturn(List.of(outro, principal));
        Saida mercado = saidaComParcela(fatura, "Mercado", 1, 1, "80.00");

        ResumoImportacao resumo = service.aplicar(importacao, faturaDoFinal("*111", item("MERCADO X", "80.00", 1, 1, "*111")),
                List.of(), List.of());

        assertThat(mercado.getDescricaoFatura()).isEqualTo("MERCADO X");
        assertThat(resumo.conciliados()).hasSize(1);
        assertThat(resumo.avisos()).isEmpty();
    }

    @Test
    void deveFalharQuandoFinalMascaradoCorrespondeAMaisDeUmCartaoPrincipal() {
        Cartao outro = Cartao.builder().nome("Outro").ultimosDigitos("9111").tipo(TipoCartao.PRINCIPAL).build();
        outro.setId(UUID.randomUUID());
        when(cartaoRepository.listarComPrincipal()).thenReturn(List.of(outro, principal));

        assertThatThrownBy(() -> service.aplicar(importacao,
                faturaDoFinal("*111", item("X", "1.00", 1, 1, "*111")), List.of(), List.of()))
                .isInstanceOf(FaturaImportacaoInvalidaException.class)
                .hasMessageContaining("mais de um cartão principal");
    }

    @Test
    void deveCriarCartaoVirtualTemporarioParaFinalDesconhecido() {
        when(cartaoRepository.save(any(Cartao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ResumoImportacao resumo = service.aplicar(importacao, fatura(
                        itemDatado("UBER *TRIP", "25.00", "2026-09-02", "7777"),
                        itemDatado("IFOOD", "40.00", "2026-09-05", "7777")),
                List.of(), List.of());

        ArgumentCaptor<Cartao> cartaoCaptor = ArgumentCaptor.forClass(Cartao.class);
        verify(cartaoRepository).save(cartaoCaptor.capture());
        Cartao virtual = cartaoCaptor.getValue();
        assertThat(virtual.getUltimosDigitos()).isEqualTo("7777");
        assertThat(virtual.getTipo()).isEqualTo(TipoCartao.ADICIONAL);
        assertThat(virtual.isVirtual()).isTrue();
        assertThat(virtual.getCartaoPrincipal()).isSameAs(principal);
        assertThat(virtual.getValidoAte()).isEqualTo(LocalDate.of(2026, 9, 5));

        ArgumentCaptor<PreSaida> preSaidaCaptor = ArgumentCaptor.forClass(PreSaida.class);
        verify(preSaidaRepository, times(2)).save(preSaidaCaptor.capture());
        assertThat(preSaidaCaptor.getAllValues()).allSatisfy(p -> assertThat(p.getCartao()).isSameAs(virtual));
        assertThat(resumo.avisos()).containsExactly(
                "Cartão virtual final 7777 criado como adicional de Principal (válido até 05/09/2026).");
    }

    @Test
    void planejarNaoDeveCriarCartaoTemporario() {
        service.planejar(importacao.getId(), fatura(itemDatado("UBER", "25.00", "2026-09-02", "7777")));

        verify(cartaoRepository, never()).save(any());
    }

    @Test
    void naoDeveCriarCartaoParaFinalMascaradoOuDeOutroPrincipal() {
        Cartao outro = Cartao.builder().nome("Outro").ultimosDigitos("8888").tipo(TipoCartao.PRINCIPAL).build();
        outro.setId(UUID.randomUUID());
        Cartao adicional = Cartao.builder().nome("Adicional").ultimosDigitos("3333").tipo(TipoCartao.ADICIONAL)
                .cartaoPrincipal(principal).build();
        adicional.setId(UUID.randomUUID());
        when(cartaoRepository.listarComPrincipal()).thenReturn(List.of(principal, adicional, outro));

        ResumoImportacao resumo = service.aplicar(importacao, fatura(
                        itemDatado("LOJA C", "30.00", "2026-09-04", "3333"),
                        itemDatado("LOJA A", "10.00", "2026-09-02", "*555"),
                        itemDatado("LOJA B", "20.00", "2026-09-03", "8888")),
                List.of(), List.of());

        verify(cartaoRepository, never()).save(any());
        assertThat(resumo.avisos()).contains(
                "O final 555 aparece na fatura, mas não é um cartão de Principal.",
                "O final 8888 aparece na fatura, mas não é um cartão de Principal.");
    }

    @Test
    void deveUsarCartaoTemporarioJaCadastradoAoReprocessar() {
        Cartao virtual = Cartao.builder().nome("Virtual 7777").ultimosDigitos("7777").tipo(TipoCartao.ADICIONAL)
                .virtual(true).cartaoPrincipal(principal).validoAte(LocalDate.of(2026, 9, 2)).build();
        virtual.setId(UUID.randomUUID());
        when(cartaoRepository.listarComPrincipal()).thenReturn(List.of(principal, virtual));

        ResumoImportacao resumo = service.aplicar(importacao,
                fatura(itemDatado("UBER", "25.00", "2026-09-02", "7777")), List.of(), List.of());

        verify(cartaoRepository, never()).save(any());
        ArgumentCaptor<PreSaida> captor = ArgumentCaptor.forClass(PreSaida.class);
        verify(preSaidaRepository).save(captor.capture());
        assertThat(captor.getValue().getCartao()).isSameAs(virtual);
        assertThat(resumo.avisos()).isEmpty();
    }

    private static ItemLido itemDatado(String descricao, String valor, String data, String finalCartao) {
        return new ItemLido(finalCartao, data, descricao, new BigDecimal(valor), 1, 1, "COMPRA", null, null);
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

    private static FaturaLidaIa faturaDoFinal(String finalCartao, ItemLido... itens) {
        return new FaturaLidaIa(VENCIMENTO.toString(), null, List.of(new CartaoLido(finalCartao, null)), List.of(itens));
    }

    private static ItemLido item(String descricao, String valor, int parcela, int total, String finalCartao) {
        return new ItemLido(finalCartao, null, descricao, new BigDecimal(valor), parcela, total, "COMPRA", null, null);
    }
}
