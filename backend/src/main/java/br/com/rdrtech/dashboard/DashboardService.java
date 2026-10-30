package br.com.rdrtech.dashboard;

import br.com.rdrtech.bucket.BucketRepository;
import br.com.rdrtech.dashboard.dto.DashboardItemResponse;
import br.com.rdrtech.dashboard.dto.DashboardMonthlyAmountResponse;
import br.com.rdrtech.dashboard.dto.DashboardProjectionMonthResponse;
import br.com.rdrtech.dashboard.dto.DashboardResponse;
import br.com.rdrtech.entrada.EntradaOcorrenciaRepository;
import br.com.rdrtech.entrada.EntradaService;
import br.com.rdrtech.saida.SaidaResponsavelRepository;
import br.com.rdrtech.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final SaidaResponsavelRepository saidaResponsavelRepository;
    private final BucketRepository bucketRepository;
    private final EntradaService entradaService;
    private final EntradaOcorrenciaRepository entradaOcorrenciaRepository;

    /** Transação de escrita: as ocorrências das entradas recorrentes do mês podem ser geradas aqui. */
    @Transactional
    public DashboardResponse consultar(int mes, int ano) {
        LocalDate inicio = inicioMes(mes, ano);
        LocalDate fim = inicio.plusMonths(1);
        UUID usuarioId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Não foi possível identificar o usuário autenticado."));

        BigDecimal gastosLancados = saidaResponsavelRepository
                .somarPartesUsuarioNoPeriodo(usuarioId, inicio, fim, false);
        BigDecimal bucketsReservados = bucketRepository.somarValorMensalDoUsuario(usuarioId);
        BigDecimal contasFixasGeradas = saidaResponsavelRepository
                .somarPartesUsuarioNoPeriodo(usuarioId, inicio, fim, true);
        BigDecimal contasFixasPrevistas = saidaResponsavelRepository
                .somarPartesFixasPrevistasUsuarioNoPeriodo(usuarioId, inicio.minusMonths(1), inicio);
        BigDecimal contasFixas = contasFixasGeradas.add(contasFixasPrevistas);
        BigDecimal totalPrevisto = gastosLancados.add(bucketsReservados).add(contasFixas);
        entradaService.garantirOcorrenciasAte(YearMonth.from(inicio));
        BigDecimal receitasPrevistas = entradaOcorrenciaRepository.somarPrevistasNoPeriodo(inicio, fim);
        BigDecimal receitasRecebidas = entradaOcorrenciaRepository.somarRecebidasNoPeriodo(inicio, fim);

        return new DashboardResponse(
                mes,
                ano,
                gastosLancados,
                bucketsReservados,
                contasFixas,
                contasFixasPrevistas,
                totalPrevisto,
                receitasPrevistas,
                receitasRecebidas,
                receitasPrevistas.subtract(totalPrevisto));
    }

    @Transactional
    public List<DashboardProjectionMonthResponse> consultarProjecao(int mes, int ano, int quantidade) {
        if (quantidade < 3 || quantidade > 24) {
            throw new IllegalArgumentException("A projeção deve conter entre 3 e 24 meses.");
        }
        LocalDate inicio = inicioMes(mes, ano);
        LocalDate fim = inicio.plusMonths(quantidade);
        UUID usuarioId = usuarioAutenticado();

        Map<YearMonth, BigDecimal> gastosPorMes = indexarPorMes(
                saidaResponsavelRepository.somarPartesUsuarioAgrupadasPorMes(usuarioId, inicio, fim, false));
        Map<YearMonth, BigDecimal> fixasGeradasPorMes = indexarPorMes(
                saidaResponsavelRepository.somarPartesUsuarioAgrupadasPorMes(usuarioId, inicio, fim, true));
        Map<YearMonth, BigDecimal> fixasPrevistasPorMes = indexarPorMes(
                saidaResponsavelRepository.somarPartesFixasPrevistasUsuarioAgrupadasPorMes(
                        usuarioId, inicio.minusMonths(1), fim.minusMonths(1)));
        BigDecimal bucketsReservados = bucketRepository.somarValorMensalDoUsuario(usuarioId);
        entradaService.garantirOcorrenciasAte(YearMonth.from(fim.minusMonths(1)));
        Map<YearMonth, BigDecimal> receitasPorMes = indexarPorMes(
                entradaOcorrenciaRepository.somarPrevistasAgrupadasPorMes(inicio, fim));

        return IntStream.range(0, quantidade)
                .mapToObj(indice -> {
                    LocalDate data = inicio.plusMonths(indice);
                    YearMonth competencia = YearMonth.from(data);
                    BigDecimal gastosLancados = gastosPorMes.getOrDefault(competencia, BigDecimal.ZERO);
                    BigDecimal contasFixasPrevistas = fixasPrevistasPorMes.getOrDefault(
                            competencia.minusMonths(1), BigDecimal.ZERO);
                    BigDecimal contasFixas = fixasGeradasPorMes.getOrDefault(competencia, BigDecimal.ZERO)
                            .add(contasFixasPrevistas);
                    BigDecimal totalPrevisto = gastosLancados.add(bucketsReservados).add(contasFixas);
                    BigDecimal receitasPrevistas = receitasPorMes.getOrDefault(competencia, BigDecimal.ZERO);
                    return new DashboardProjectionMonthResponse(
                            competencia.getMonthValue(),
                            competencia.getYear(),
                            gastosLancados,
                            bucketsReservados,
                            contasFixas,
                            contasFixasPrevistas,
                            totalPrevisto,
                            receitasPrevistas,
                            receitasPrevistas.subtract(totalPrevisto));
                })
                .toList();
    }

    /** Saídas do mês sem as contas fixas, que têm card próprio. */
    public List<DashboardItemResponse> listarSaidas(int mes, int ano) {
        LocalDate inicio = inicioMes(mes, ano);
        return saidaResponsavelRepository.listarPartesUsuarioDetalhadasNoPeriodo(
                usuarioAutenticado(), inicio, inicio.plusMonths(1), false);
    }

    public List<DashboardItemResponse> listarBuckets() {
        return bucketRepository.listarDoUsuarioDetalhados(usuarioAutenticado());
    }

    /**
     * Contas fixas do mês: as já lançadas ({@code GERADA}) e a previsão das que faltam, tiradas do mês anterior
     * e projetadas para o mesmo dia deste mês ({@code PREVISTA}).
     */
    public List<DashboardItemResponse> listarContasFixas(int mes, int ano) {
        LocalDate inicio = inicioMes(mes, ano);
        YearMonth competencia = YearMonth.from(inicio);
        UUID usuarioId = usuarioAutenticado();
        Stream<DashboardItemResponse> geradas = saidaResponsavelRepository
                .listarPartesUsuarioDetalhadasNoPeriodo(usuarioId, inicio, inicio.plusMonths(1), true).stream()
                .map(item -> comSituacao(item, item.vencimento(), item.numeroParcela(),
                        item.quantidadeParcelas(), DashboardItemResponse.GERADA));
        Stream<DashboardItemResponse> previstas = saidaResponsavelRepository
                .listarPartesFixasPrevistasUsuarioDetalhadasNoPeriodo(usuarioId, inicio.minusMonths(1), inicio)
                .stream()
                .map(item -> comSituacao(item,
                        competencia.atDay(Math.min(item.vencimento().getDayOfMonth(), competencia.lengthOfMonth())),
                        null, null, DashboardItemResponse.PREVISTA));
        return Stream.concat(geradas, previstas)
                .sorted(Comparator.comparing(DashboardItemResponse::vencimento)
                        .thenComparing(DashboardItemResponse::descricao, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public List<DashboardItemResponse> listarEntradas(int mes, int ano) {
        LocalDate inicio = inicioMes(mes, ano);
        entradaService.garantirOcorrenciasAte(YearMonth.from(inicio));
        return entradaOcorrenciaRepository.listarPrevistasDetalhadasNoPeriodo(inicio, inicio.plusMonths(1));
    }

    private LocalDate inicioMes(int mes, int ano) {
        try {
            return LocalDate.of(ano, mes, 1);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Informe uma competência válida.", exception);
        }
    }

    private UUID usuarioAutenticado() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Não foi possível identificar o usuário autenticado."));
    }

    private DashboardItemResponse comSituacao(
            DashboardItemResponse item,
            LocalDate vencimento,
            Integer numeroParcela,
            Integer quantidadeParcelas,
            String situacao) {
        return new DashboardItemResponse(item.descricao(), item.categoriaNome(), item.subcategoriaNome(),
                vencimento, numeroParcela, quantidadeParcelas, item.valor(), situacao);
    }

    private Map<YearMonth, BigDecimal> indexarPorMes(List<DashboardMonthlyAmountResponse> valores) {
        return valores.stream().collect(Collectors.toMap(
                valor -> YearMonth.of(valor.ano(), valor.mes()),
                DashboardMonthlyAmountResponse::valor,
                BigDecimal::add));
    }
}
