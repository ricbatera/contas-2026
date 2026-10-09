package br.com.rdrtech.contabancaria;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaBancariaRepository extends JpaRepository<ContaBancaria, UUID> {

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    /**
     * O {@code findById} herdado de {@link JpaRepository} usa {@code EntityManager.find()}, que
     * carrega por chave primária ignorando o filtro Hibernate de {@link br.com.rdrtech.tenant.TenantAwareEntity}
     * — permitindo acesso a contas de outro usuário. Uma consulta JPQL explícita passa pelo
     * parser HQL, onde o filtro é aplicado normalmente.
     */
    @Query("select c from ContaBancaria c where c.id = :id")
    Optional<ContaBancaria> buscarPorId(@Param("id") UUID id);
}
