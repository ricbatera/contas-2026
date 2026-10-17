package br.com.rdrtech.presaida;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreSaidaRepository extends JpaRepository<PreSaida, UUID> {

    @Query("select p from PreSaida p where p.id = :id")
    Optional<PreSaida> buscarPorId(@Param("id") UUID id);

    @Query("select p from PreSaida p order by p.competencia desc, p.descricao, p.numeroParcela")
    List<PreSaida> listar();

    @Query("select p from PreSaida p where p.status = :status order by p.competencia desc, p.descricao, p.numeroParcela")
    List<PreSaida> listarPorStatus(@Param("status") StatusPreSaida status);

    @Query("select p from PreSaida p where p.importacao.id = :importacaoId order by p.descricao, p.numeroParcela")
    List<PreSaida> listarPorImportacao(@Param("importacaoId") UUID importacaoId);

    /**
     * Pré-saídas já geradas para a mesma fatura (por qualquer importação) que ainda não viraram
     * saída: pendentes ou rejeitadas. Uma reimportação as reaproveita em vez de duplicar.
     */
    @Query("""
            select p from PreSaida p
            where p.importacao.cartaoPrincipal.id = :cartaoPrincipalId
              and p.competencia = :competencia
              and p.status in (br.com.rdrtech.presaida.StatusPreSaida.PENDENTE,
                               br.com.rdrtech.presaida.StatusPreSaida.REJEITADA)
            """)
    List<PreSaida> listarNaoAprovadasDaFatura(
            @Param("cartaoPrincipalId") UUID cartaoPrincipalId, @Param("competencia") int competencia);

    @Modifying
    @Query("delete from PreSaida p where p.importacao.id = :importacaoId and p.status = br.com.rdrtech.presaida.StatusPreSaida.PENDENTE")
    void excluirPendentesDaImportacao(@Param("importacaoId") UUID importacaoId);
}
