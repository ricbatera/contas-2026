package br.com.rdrtech.entrada;

import java.time.LocalDate;
import java.util.UUID;

/** Última competência gerada de uma entrada recorrente ({@code ultimaCompetencia} nula se nenhuma). */
public record HorizonteEntrada(UUID id, LocalDate dataInicial, LocalDate dataFinal, LocalDate ultimaCompetencia) {
}
