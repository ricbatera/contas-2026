package br.com.rdrtech.dashboard;

import br.com.rdrtech.bucket.BucketRepository;
import br.com.rdrtech.dashboard.dto.DashboardItemResponse;
import br.com.rdrtech.dashboard.dto.DashboardMonthlyAmountResponse;
import br.com.rdrtech.dashboard.dto.DashboardProjectionMonthResponse;
import br.com.rdrtech.dashboard.dto.DashboardResponse;
import br.com.rdrtech.saida.SaidaResponsavelRepository;
import br.com.rdrtech.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final SaidaResponsavelRepository saidaResponsavelRepository;
    private final BucketRepository bucketRepository;

    public DashboardResponse consultar(int mes, int ano) {
        LocalDate inicio = inicioMes(mes, ano);
        LocalDate fim = inicio.plusMonths(1);
        UUID usuarioId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Não foi possível identificar o usuário autenticado."));

        BigDecimal gastosLancados = saidaResponsavelRepository
                .somarPartesUsuarioNoPeriodo(usuarioId, inicio, fim);
        BigDecimal bucketsReservados = bucketRepository.somarValorMensalDoUsuario(usuarioId);
        LocalDate inicioMesAnterior = inicio.minusMonths(1);
        BigDecimal contasFixasMesAnterior = saidaResponsavelRepository
                .somarPartesFixasUsuarioNoPeriodo(usuarioId, inicioMesAnterior, inicio);
        BigDecimal totalPrevisto = gastosLancados.add(bucketsReservados).add(contasFixasMesAnterior);

        return new DashboardResponse(
                mes, ano, gastosLancados, bucketsReservados, contasFixasMesAnterior, totalPrevisto);
    }

    public List<DashboardProjectionMonthResponse> consultarProjecao(int mes, int ano, int quantidade) {
        if (quantidade < 3 || quantidade > 24) {
            throw new IllegalArgumentException("A projeção deve conter entre 3 e 24 meses.");
        }
        LocalDate inicio = inicioMes(mes, ano);
        LocalDate fim = inicio.plusMonths(quantidade);
        UUID usuarioId = usuarioAutenticado();

        Map<YearMonth, BigDecimal> gastosPorMes = indexarPorMes(
                saidaResponsavelRepository.somarPartesUsuarioAgrupadasPorMes(usuarioId, inicio, fim));
        Map<YearMonth, BigDecimal> contasFixasPorMes = indexarPorMes(
                saidaResponsavelRepository.somarPartesFixasUsuarioAgrupadasPorMes(
                        usuarioId, inicio.minusMonths(1), fim.minusMonths(1)));
        BigDecimal bucketsReservados = bucketRepository.somarValorMensalDoUsuario(usuarioId);

        return IntStream.range(0, quantidade)
                .mapToObj(indice -> {
                    LocalDate data = inicio.plusMonths(indice);
                    YearMonth competencia = YearMonth.from(data);
                    BigDecimal gastosLancados = gastosPorMes.getOrDefault(competencia, BigDecimal.ZERO);
                    BigDecimal contasFixasMesAnterior = contasFixasPorMes.getOrDefault(
                            competencia.minusMonths(1), BigDecimal.ZERO);
                    BigDecimal totalPrevisto = gastosLancados.add(bucketsReservados).add(contasFixasMesAnterior);
                    return new DashboardProjectionMonthResponse(
                            competencia.getMonthValue(),
                            competencia.getYear(),
                            gastosLancados,
                            bucketsReservados,
                            contasFixasMesAnterior,
                            totalPrevisto);
                })
                .toList();
    }

    public List<DashboardItemResponse> listarSaidas(int mes, int ano) {
        LocalDate inicio = inicioMes(mes, ano);
        return saidaResponsavelRepository.listarPartesUsuarioDetalhadasNoPeriodo(
                usuarioAutenticado(), inicio, inicio.plusMonths(1));
    }

    public List<DashboardItemResponse> listarBuckets() {
        return bucketRepository.listarDoUsuarioDetalhados(usuarioAutenticado());
    }

    public List<DashboardItemResponse> listarContasFixasMesAnterior(int mes, int ano) {
        LocalDate inicioMesAnterior = inicioMes(mes, ano).minusMonths(1);
        return saidaResponsavelRepository.listarPartesFixasUsuarioDetalhadasNoPeriodo(
                usuarioAutenticado(), inicioMesAnterior, inicioMesAnterior.plusMonths(1));
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

    private Map<YearMonth, BigDecimal> indexarPorMes(List<DashboardMonthlyAmountResponse> valores) {
        return valores.stream().collect(Collectors.toMap(
                valor -> YearMonth.of(valor.ano(), valor.mes()),
                DashboardMonthlyAmountResponse::valor,
                BigDecimal::add));
    }
}
