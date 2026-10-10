package br.com.rdrtech.saida.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SaidaParteRequest(
        @NotNull UUID pessoaId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valorPorParcela) {
}
