package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoListaResponse;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoStatusResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaturaImportacaoRepository extends JpaRepository<FaturaImportacao, UUID> {

    /** JPQL explícito para passar pelo filtro de tenant (o {@code findById} herdado não passa). */
    @Query("select i from FaturaImportacao i where i.id = :id")
    Optional<FaturaImportacao> buscarPorId(@Param("id") UUID id);

    /** Histórico sem carregar o PDF de cada importação. */
    @Query("""
            select new br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoListaResponse(
                i.id, i.nomeArquivo, i.status, c.id, c.nome, c.ultimosDigitos, i.competencia, i.vencimento,
                i.totalPdf,
                (select count(p) from PreSaida p
                    where p.importacao = i and p.status = br.com.rdrtech.presaida.StatusPreSaida.PENDENTE),
                i.auditoria.createdAt)
            from FaturaImportacao i
            left join i.cartaoPrincipal c
            order by i.auditoria.createdAt desc
            """)
    List<FaturaImportacaoListaResponse> listar();

    /** Consulta leve (sem o PDF) usada no acompanhamento do processamento. */
    @Query("""
            select new br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoStatusResponse(
                i.id, i.nomeArquivo, i.status, i.erro,
                (select count(p) from PreSaida p
                    where p.importacao = i and p.status = br.com.rdrtech.presaida.StatusPreSaida.PENDENTE),
                i.auditoria.updatedAt)
            from FaturaImportacao i
            where i.id = :id
            """)
    Optional<FaturaImportacaoStatusResponse> buscarStatus(@Param("id") UUID id);

    @Query("select count(i) > 0 from FaturaImportacao i where i.hashSha256 = :hash and i.id <> :id")
    boolean existeOutraComHash(@Param("hash") String hash, @Param("id") UUID id);
}
