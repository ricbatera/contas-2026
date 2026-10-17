package br.com.rdrtech.bucket;

import br.com.rdrtech.dashboard.dto.DashboardItemResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BucketRepository extends JpaRepository<Bucket, UUID> {

    @Query("select coalesce(sum(b.valorMensal), 0) from Bucket b where b.usuario.id = :usuarioId")
    BigDecimal somarValorMensalDoUsuario(@Param("usuarioId") UUID usuarioId);

    @Query("""
            select new br.com.rdrtech.dashboard.dto.DashboardItemResponse(
                null,
                c.nome,
                s.nome,
                null,
                null,
                null,
                b.valorMensal)
            from Bucket b
            join b.subcategoria s
            join s.categoria c
            where b.usuario.id = :usuarioId
            order by c.nome, s.nome
            """)
    List<DashboardItemResponse> listarDoUsuarioDetalhados(@Param("usuarioId") UUID usuarioId);

    @Query("""
            select b from Bucket b
            join fetch b.subcategoria s
            join fetch s.categoria c
            left join fetch b.pessoa p
            left join fetch b.usuario u
            """)
    List<Bucket> listarComReferencias();

    @Query("""
            select b from Bucket b
            join fetch b.subcategoria s
            join fetch s.categoria c
            left join fetch b.pessoa p
            left join fetch b.usuario u
            where b.id = :id
            """)
    Optional<Bucket> buscarPorId(@Param("id") UUID id);

    @Query("""
            select count(b) > 0 from Bucket b
            where b.subcategoria.id = :subcategoriaId
              and b.usuario.id = :usuarioId
              and (:id is null or b.id <> :id)
            """)
    boolean existeParaUsuario(
            @Param("subcategoriaId") UUID subcategoriaId, @Param("usuarioId") UUID usuarioId, @Param("id") UUID id);

    @Query("""
            select count(b) > 0 from Bucket b
            where b.subcategoria.id = :subcategoriaId
              and b.pessoa.id = :pessoaId
              and (:id is null or b.id <> :id)
            """)
    boolean existeParaPessoa(
            @Param("subcategoriaId") UUID subcategoriaId, @Param("pessoaId") UUID pessoaId, @Param("id") UUID id);
}
