package br.com.rdrtech.pessoa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

    /**
     * EntityManager.find() bypasses the Hibernate tenant filter; JPQL keeps ID lookups tenant-scoped.
     */
    @Query("select p from Pessoa p where p.id = :id")
    Optional<Pessoa> buscarPorId(@Param("id") UUID id);
}
