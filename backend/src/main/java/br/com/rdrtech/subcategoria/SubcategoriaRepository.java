package br.com.rdrtech.subcategoria;

import br.com.rdrtech.categoria.TipoCategoria;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubcategoriaRepository extends JpaRepository<Subcategoria, UUID> {

    @Query("select s from Subcategoria s join fetch s.categoria where s.tipo = :tipo and s.padrao = true")
    java.util.List<Subcategoria> listarPadroesPorTipo(@Param("tipo") TipoCategoria tipo);

    @Query("select s from Subcategoria s where s.categoria.id = :categoriaId order by lower(s.nome)")
    List<Subcategoria> listarPorCategoriaId(@Param("categoriaId") UUID categoriaId);

    @Query("select s from Subcategoria s join fetch s.categoria where s.id = :id")
    Optional<Subcategoria> buscarPorId(@Param("id") UUID id);

    boolean existsByCategoria_IdAndNomeIgnoreCase(UUID categoriaId, String nome);

    boolean existsByCategoria_IdAndNomeIgnoreCaseAndIdNot(UUID categoriaId, String nome, UUID id);
}
