package br.com.rdrtech.cartao;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartaoRepository extends JpaRepository<Cartao, UUID> {

    boolean existsByBandeiraAndUltimosDigitos(Bandeira bandeira, String ultimosDigitos);

    boolean existsByBandeiraAndUltimosDigitosAndIdNot(Bandeira bandeira, String ultimosDigitos, UUID id);

    /**
     * O {@code findById} herdado de {@link JpaRepository} usa {@code EntityManager.find()}, que
     * carrega por chave primária ignorando o filtro Hibernate de {@link br.com.rdrtech.tenant.TenantAwareEntity}
     * — permitindo acesso a cartões de outro usuário. Uma consulta JPQL explícita passa pelo
     * parser HQL, onde o filtro é aplicado normalmente.
     */
    @Query("select c from Cartao c where c.id = :id")
    Optional<Cartao> buscarPorId(@Param("id") UUID id);

    @Query("select c from Cartao c where c.tipo = br.com.rdrtech.cartao.TipoCartao.PRINCIPAL")
    java.util.List<Cartao> listarPrincipais();

    @Query("select c from Cartao c where c.id = :id and c.tipo = br.com.rdrtech.cartao.TipoCartao.PRINCIPAL")
    Optional<Cartao> buscarPrincipalPorId(@Param("id") UUID id);

    boolean existsByCartaoPrincipal_Id(UUID cartaoPrincipalId);

    /** Todos os cartões do usuário com o principal carregado, para casar finais (às vezes parciais) da fatura. */
    @Query("select c from Cartao c left join fetch c.cartaoPrincipal")
    java.util.List<Cartao> listarComPrincipal();
}
