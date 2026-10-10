package br.com.rdrtech.saida;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaidaRepository extends JpaRepository<Saida, UUID> {

    @Query("select distinct s from Saida s where s.id = :id")
    Optional<Saida> buscarPorId(@Param("id") UUID id);

    boolean existsByCartao_Id(UUID cartaoId);

    boolean existsByCategoria_Id(UUID categoriaId);

    boolean existsBySubcategoria_Id(UUID subcategoriaId);

    boolean existsByResponsabilidades_Pessoa_Id(UUID pessoaId);
}
