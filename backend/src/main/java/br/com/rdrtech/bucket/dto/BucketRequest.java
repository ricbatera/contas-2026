package br.com.rdrtech.bucket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record BucketRequest(
        @NotNull UUID subcategoriaId,
        UUID responsavelPessoaId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valorMensal) {
}
