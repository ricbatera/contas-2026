package br.com.rdrtech.saida.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GerarContasFixasRequest(
        @NotNull @Min(1) @Max(12) Integer mes,
        @NotNull @Min(2000) @Max(2100) Integer ano,
        @NotEmpty @Valid List<@NotNull GerarContaFixaItemRequest> itens) {
}
