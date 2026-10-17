package br.com.rdrtech.ia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProvedorIaRepository extends JpaRepository<ProvedorIa, UUID> {

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    List<ProvedorIa> findByPadraoTrue();

    Optional<ProvedorIa> findFirstByPadraoTrueAndAtivoTrue();

    /** Consulta JPQL para passar pelo filtro de tenant (o {@code findById} herdado o ignora). */
    @Query("select p from ProvedorIa p where p.id = :id")
    Optional<ProvedorIa> buscarPorId(@Param("id") UUID id);
}
