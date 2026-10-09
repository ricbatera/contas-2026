package br.com.rdrtech.contabancaria.dto;

import br.com.rdrtech.contabancaria.TipoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ContaBancariaRequest(
        @NotBlank @Size(max = 50) String nome,
        @NotBlank @Size(max = 100) String banco,
        @NotNull TipoConta tipoConta,
        @Size(max = 10) String agencia,
        @Size(max = 20) String numeroConta,
        @NotNull @PositiveOrZero BigDecimal saldoInicial,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato hexadecimal, ex: #FF5733")
        String cor,
        boolean ativo) {
}
