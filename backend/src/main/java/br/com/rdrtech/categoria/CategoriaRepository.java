package br.com.rdrtech.categoria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    @Query("select distinct c from Categoria c left join fetch c.subcategorias")
    List<Categoria> listarComSubcategorias();

    @Query("select distinct c from Categoria c left join fetch c.subcategorias where c.id = :id")
    Optional<Categoria> buscarPorId(@Param("id") UUID id);

    boolean existsByTipoAndNomeIgnoreCase(TipoCategoria tipo, String nome);

    boolean existsByTipoAndNomeIgnoreCaseAndIdNot(TipoCategoria tipo, String nome, UUID id);
}
