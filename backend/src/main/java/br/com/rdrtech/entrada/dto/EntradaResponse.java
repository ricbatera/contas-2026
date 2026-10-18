package br.com.rdrtech.entrada.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EntradaResponse(
        UUID id,
        String descricao,
        BigDecimal valor,
        boolean recorrente,
        LocalDate dataInicial,
        LocalDate dataFinal,
        UUID categoriaId,
        String categoriaNome,
        UUID subcategoriaId,
        String subcategoriaNome,
        UUID contaBancariaId,
        String contaBancariaNome,
        LocalDateTime criadoEm) {
}
