package br.com.rdrtech.fatura;

import br.com.rdrtech.fatura.dto.FaturaCompetenciaResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

    @Query("select f from Fatura f where f.cartaoPrincipal.id = :cartaoId and f.competencia = :competencia")
    Optional<Fatura> buscarPorCartaoECompetencia(
            @Param("cartaoId") UUID cartaoId, @Param("competencia") int competencia);

    @Query("""
            select new br.com.rdrtech.fatura.dto.FaturaCompetenciaResponse(
                f.competencia, f.vencimento, coalesce(sum(p.valor), 0))
            from Fatura f
            left join f.parcelas p
            where f.cartaoPrincipal.id = :cartaoId
            group by f.id, f.competencia, f.vencimento
            order by f.competencia desc
            """)
    List<FaturaCompetenciaResponse> listarCompetenciasPorCartao(@Param("cartaoId") UUID cartaoId);

    boolean existsByCartaoPrincipal_Id(UUID cartaoPrincipalId);
}
