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
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """)
    BigDecimal somarPartesUsuarioNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
            select coalesce(sum(r.valorPorParcela), 0)
            from SaidaResponsavel r
            join r.saida s
            join s.parcelas p
            where r.usuario.id = :usuarioId
              and s.contaFixa = true
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            """)
    BigDecimal somarPartesFixasUsuarioNoPeriodo(
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
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            group by year(p.vencimento), month(p.vencimento)
            """)
    List<DashboardMonthlyAmountResponse> somarPartesUsuarioAgrupadasPorMes(
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
              and s.contaFixa = true
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            group by year(p.vencimento), month(p.vencimento)
            """)
    List<DashboardMonthlyAmountResponse> somarPartesFixasUsuarioAgrupadasPorMes(
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
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            order by p.vencimento, s.descricao
            """)
    List<DashboardItemResponse> listarPartesUsuarioDetalhadasNoPeriodo(
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
              and s.contaFixa = true
              and p.vencimento >= :inicio
              and p.vencimento < :fim
            order by p.vencimento, s.descricao
            """)
    List<DashboardItemResponse> listarPartesFixasUsuarioDetalhadasNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
