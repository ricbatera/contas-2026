package br.com.rdrtech.entrada.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EntradaRequest(
        @NotBlank @Size(max = 120) String descricao,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valor,
        boolean recorrente,
        @NotNull LocalDate dataInicial,
        LocalDate dataFinal,
        @NotNull UUID categoriaId,
        @NotNull UUID subcategoriaId,
        UUID contaBancariaId) {
}
