package br.com.rdrtech.saida.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SaidaParteRequest(
        @NotNull UUID pessoaId,
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal valorPorParcela) {
}
