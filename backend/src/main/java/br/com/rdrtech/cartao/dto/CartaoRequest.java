package br.com.rdrtech.cartao.dto;

import br.com.rdrtech.cartao.Bandeira;
import br.com.rdrtech.cartao.TipoCartao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CartaoRequest(
        @NotBlank @Size(max = 50) String nome,
        @NotBlank @Size(max = 100) String banco,
        @NotNull Bandeira bandeira,
        @NotBlank
        @Pattern(regexp = "^\\d{4}$", message = "Últimos 4 dígitos devem conter exatamente 4 números")
        String ultimosDigitos,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato hexadecimal, ex: #FF5733")
        String cor,
        TipoCartao tipo,
        Boolean virtual,
        UUID cartaoPrincipalId,
        Boolean ativo) {

    public CartaoRequest(
            String nome, String banco, Bandeira bandeira, String ultimosDigitos, String cor) {
        this(nome, banco, bandeira, ultimosDigitos, cor, null, false, null, true);
    }

    public CartaoRequest(
            String nome,
            String banco,
            Bandeira bandeira,
            String ultimosDigitos,
            String cor,
            TipoCartao tipo,
            Boolean virtual,
            UUID cartaoPrincipalId) {
        this(nome, banco, bandeira, ultimosDigitos, cor, tipo, virtual, cartaoPrincipalId, true);
    }
}
