package br.com.rdrtech.entrada;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {

    /** JPQL explícita para passar pelo filtro de tenant, que o {@code findById} ignora. */
    @Query("select e from Entrada e where e.id = :id")
    Optional<Entrada> buscarPorId(@Param("id") UUID id);

    /** Trava as entradas para que duas consultas simultâneas não gerem a mesma ocorrência. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entrada e where e.id in :ids")
    List<Entrada> travarPorIds(@Param("ids") Collection<UUID> ids);

    /** Recorrentes com a última competência gerada, para estender o horizonte das ocorrências. */
    @Query("""
            select new br.com.rdrtech.entrada.HorizonteEntrada(e.id, e.dataInicial, e.dataFinal, max(o.competencia))
            from Entrada e
            left join e.ocorrencias o
            where e.recorrente = true
            group by e.id, e.dataInicial, e.dataFinal
            """)
    List<HorizonteEntrada> listarHorizontesRecorrentes();

    boolean existsByCategoria_Id(UUID categoriaId);

    boolean existsBySubcategoria_Id(UUID subcategoriaId);

    boolean existsByContaBancaria_Id(UUID contaBancariaId);
}
