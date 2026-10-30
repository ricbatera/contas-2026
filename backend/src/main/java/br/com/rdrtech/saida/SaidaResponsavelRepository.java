package br.com.rdrtech.saida;

import br.com.rdrtech.dashboard.dto.DashboardItemResponse;
import br.com.rdrtech.dashboard.dto.DashboardMonthlyAmountResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaidaResponsavelRepository extends JpaRepository<SaidaResponsavel, UUID> {

    /**
     * A conta fixa do mês M só vira previsão de M+1 enquanto a série dela não tiver saída em M+1
     * (gerada pelo "Gerar contas fixas"); senão ela seria contada duas vezes.
     */
    String SEM_OCORRENCIA_NO_MES_SEGUINTE = """
              and not exists (
                  select 1 from Saida s2
                  join s2.parcelas p2
                  where s2.serieContaFixaId = s.serieContaFixaId
                    and year(p2.vencimento) * 12 + month(p2.vencimento)
                        = year(p.vencimento) * 12 + month(p.vencimento) + 1)
            """;

    @Query("""
            select coalesce(sum(r.valorPorParcela), 0)
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where s.subcategoria.id = :subcategoriaId
              and r.pessoa.id = :pessoaId
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """)
    BigDecimal somarPartePessoa(
            @Param("subcategoriaId") UUID subcategoriaId,
            @Param("pessoaId") UUID pessoaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
            select coalesce(sum(r.valorPorParcela), 0)
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where s.subcategoria.id = :subcategoriaId
              and r.usuario.id = :usuarioId
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """)
    BigDecimal somarParteUsuario(
            @Param("subcategoriaId") UUID subcategoriaId,
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
            select coalesce(sum(r.valorPorParcela), 0)
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where r.usuario.id = :usuarioId
              and s.contaFixa = :contaFixa
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """)
    BigDecimal somarPartesUsuarioNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("contaFixa") boolean contaFixa);

    @Query("""
            select coalesce(sum(r.valorPorParcela), 0)
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where r.usuario.id = :usuarioId
              and s.contaFixa = true
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """ + SEM_OCORRENCIA_NO_MES_SEGUINTE)
    BigDecimal somarPartesFixasPrevistasUsuarioNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
            select new br.com.rdrtech.dashboard.dto.DashboardMonthlyAmountResponse(
                year(p.vencimento),
                month(p.vencimento),
                sum(r.valorPorParcela))
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where r.usuario.id = :usuarioId
              and s.contaFixa = :contaFixa
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            group by year(p.vencimento), month(p.vencimento)
            """)
    List<DashboardMonthlyAmountResponse> somarPartesUsuarioAgrupadasPorMes(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("contaFixa") boolean contaFixa);

    @Query("""
            select new br.com.rdrtech.dashboard.dto.DashboardMonthlyAmountResponse(
                year(p.vencimento),
                month(p.vencimento),
                sum(r.valorPorParcela))
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where r.usuario.id = :usuarioId
              and s.contaFixa = true
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """ + SEM_OCORRENCIA_NO_MES_SEGUINTE + """
            group by year(p.vencimento), month(p.vencimento)
            """)
    List<DashboardMonthlyAmountResponse> somarPartesFixasPrevistasUsuarioAgrupadasPorMes(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
            select new br.com.rdrtech.dashboard.dto.DashboardItemResponse(
                s.descricao,
                s.categoria.nome,
                s.subcategoria.nome,
                p.vencimento,
                p.numero,
                s.quantidadeParcelas,
                r.valorPorParcela)
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where r.usuario.id = :usuarioId
              and s.contaFixa = :contaFixa
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            order by p.vencimento, s.descricao
            """)
    List<DashboardItemResponse> listarPartesUsuarioDetalhadasNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("contaFixa") boolean contaFixa);

    @Query("""
            select new br.com.rdrtech.dashboard.dto.DashboardItemResponse(
                s.descricao,
                s.categoria.nome,
                s.subcategoria.nome,
                p.vencimento,
                p.numero,
                s.quantidadeParcelas,
                r.valorPorParcela)
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where r.usuario.id = :usuarioId
              and s.contaFixa = true
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """ + SEM_OCORRENCIA_NO_MES_SEGUINTE + """
            order by p.vencimento, s.descricao
            """)
    List<DashboardItemResponse> listarPartesFixasPrevistasUsuarioDetalhadasNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
