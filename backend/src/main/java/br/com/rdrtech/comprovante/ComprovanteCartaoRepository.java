package br.com.rdrtech.comprovante;

import br.com.rdrtech.comprovante.dto.ComprovanteListaResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComprovanteCartaoRepository extends JpaRepository<ComprovanteCartao, UUID> {

    /** JPQL explícito para passar pelo filtro de tenant (o {@code findById} herdado não passa). */
    @Query("select c from ComprovanteCartao c where c.id = :id")
    Optional<ComprovanteCartao> buscarPorId(@Param("id") UUID id);

    /** Lista sem carregar a imagem de cada comprovante; pendentes primeiro. */
    @Query("""
            select new br.com.rdrtech.comprovante.dto.ComprovanteListaResponse(
                c.id, c.status, c.modalidade, c.descricao, c.estabelecimento, c.dataCompra, c.valorTotal,
                c.quantidadeParcelas, k.id, k.nome, k.ultimosDigitos, c.erroLeitura, c.auditoria.createdAt)
            from ComprovanteCartao c
            left join c.cartao k
            order by case when c.status = br.com.rdrtech.comprovante.StatusComprovante.PENDENTE then 0 else 1 end,
                c.auditoria.createdAt desc
            """)
    List<ComprovanteListaResponse> listar();

    @Query("select count(c) > 0 from ComprovanteCartao c where c.hashSha256 = :hash and c.id <> :id")
    boolean existeOutroComHash(@Param("hash") String hash, @Param("id") UUID id);
}
