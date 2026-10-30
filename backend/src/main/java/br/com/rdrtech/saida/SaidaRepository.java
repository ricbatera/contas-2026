package br.com.rdrtech.saida;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaidaRepository extends JpaRepository<Saida, UUID> {

    @Query("select distinct s from Saida s where s.id = :id")
    Optional<Saida> buscarPorId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Saida s where s.id = :id")
    Optional<Saida> buscarPorIdParaAtualizar(@Param("id") UUID id);

    /** Saídas de séries de conta fixa com parcela antes de {@code inicio}, para achar a última de cada série. */
    @Query("""
            select distinct s from Saida s
            join s.parcelas p
            where s.serieContaFixaId is not null
              and p.vencimento < :inicio
            """)
    List<Saida> listarDeSeriesComParcelaAntesDe(@Param("inicio") LocalDate inicio);

    /** Saídas com parcela no período, de qualquer série ou lançadas à mão. */
    @Query("""
            select distinct s from Saida s
            join s.parcelas p
            where p.vencimento >= :inicio
              and p.vencimento < :fim
            """)
    List<Saida> listarComParcelaNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    boolean existsByCartao_Id(UUID cartaoId);

    boolean existsByCategoria_Id(UUID categoriaId);

    boolean existsBySubcategoria_Id(UUID subcategoriaId);

    boolean existsByResponsabilidades_Pessoa_Id(UUID pessoaId);
}
