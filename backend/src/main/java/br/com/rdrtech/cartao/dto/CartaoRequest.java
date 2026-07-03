package br.com.rdrtech.cartao.dto;

import br.com.rdrtech.cartao.Bandeira;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CartaoRequest(
        @NotBlank @Size(max = 50) String nome,
        @NotBlank @Size(max = 100) String banco,
        @NotNull Bandeira bandeira,
        @NotBlank
        @Pattern(regexp = "^\\d{4}$", message = "Últimos 4 dígitos devem conter exatamente 4 números")
        String ultimosDigitos,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato hexadecimal, ex: #FF5733")
        String cor) {
}
