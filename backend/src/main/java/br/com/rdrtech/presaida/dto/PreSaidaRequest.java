package br.com.rdrtech.presaida.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Ajustes do usuário na pré-saída antes de aprovar. */
public record PreSaidaRequest(
        @NotBlank @Size(max = 120) String descricao,
        UUID cartaoId,
        UUID categoriaId,
        UUID subcategoriaId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valorPorParcela,
        @NotNull @Min(1) @Max(60) Integer numeroParcela,
        @NotNull @Min(1) @Max(60) Integer quantidadeParcelas) {
}
