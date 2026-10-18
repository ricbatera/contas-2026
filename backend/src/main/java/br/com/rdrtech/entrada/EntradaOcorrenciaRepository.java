package br.com.rdrtech.entrada;

import br.com.rdrtech.dashboard.dto.DashboardItemResponse;
import br.com.rdrtech.dashboard.dto.DashboardMonthlyAmountResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaOcorrenciaRepository extends JpaRepository<EntradaOcorrencia, UUID> {

    @Query("""
            select o from EntradaOcorrencia o
            join fetch o.entrada e
            join fetch e.categoria
            join fetch e.subcategoria
            left join fetch e.contaBancaria
            where o.id = :id
            """)
    Optional<EntradaOcorrencia> buscarPorId(@Param("id") UUID id);

    @Query("""
            select o from EntradaOcorrencia o
            join fetch o.entrada e
            join fetch e.categoria
            join fetch e.subcategoria
            left join fetch e.contaBancaria
            where o.competencia = :competencia
            order by o.dataPrevista, e.descricao
            """)
    List<EntradaOcorrencia> listarDaCompetencia(@Param("competencia") LocalDate competencia);

    /** Valor esperado do período: o recebido quando houver, senão o previsto; canceladas não contam. */
    @Query("""
            select coalesce(sum(case when o.status = br.com.rdrtech.entrada.StatusOcorrencia.RECEBIDA
                    then o.valorRecebido else o.valorPrevisto end), 0)
            from EntradaOcorrencia o
            where o.status <> br.com.rdrtech.entrada.StatusOcorrencia.CANCELADA
            and o.competencia >= :inicio and o.competencia < :fim
            """)
    BigDecimal somarPrevistasNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select coalesce(sum(o.valorRecebido), 0)
            from EntradaOcorrencia o
            where o.status = br.com.rdrtech.entrada.StatusOcorrencia.RECEBIDA
            and o.competencia >= :inicio and o.competencia < :fim
            """)
    BigDecimal somarRecebidasNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select new br.com.rdrtech.dashboard.dto.DashboardMonthlyAmountResponse(
                year(o.competencia),
                month(o.competencia),
                sum(case when o.status = br.com.rdrtech.entrada.StatusOcorrencia.RECEBIDA
                    then o.valorRecebido else o.valorPrevisto end))
            from EntradaOcorrencia o
            where o.status <> br.com.rdrtech.entrada.StatusOcorrencia.CANCELADA
            and o.competencia >= :inicio and o.competencia < :fim
            group by year(o.competencia), month(o.competencia)
            """)
    List<DashboardMonthlyAmountResponse> somarPrevistasAgrupadasPorMes(
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select new br.com.rdrtech.dashboard.dto.DashboardItemResponse(
                e.descricao,
                c.nome,
                s.nome,
                coalesce(o.dataRecebimento, o.dataPrevista),
                cast(null as Integer),
                cast(null as Integer),
                case when o.status = br.com.rdrtech.entrada.StatusOcorrencia.RECEBIDA
                    then o.valorRecebido else o.valorPrevisto end)
            from EntradaOcorrencia o
            join o.entrada e
            join e.categoria c
            join e.subcategoria s
            where o.status <> br.com.rdrtech.entrada.StatusOcorrencia.CANCELADA
            and o.competencia >= :inicio and o.competencia < :fim
            order by o.dataPrevista, e.descricao
            """)
    List<DashboardItemResponse> listarPrevistasDetalhadasNoPeriodo(
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
