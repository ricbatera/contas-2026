package br.com.rdrtech.ia.dto;

import br.com.rdrtech.ia.TipoProvedorIa;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code apiKey} é opcional na edição: vazio mantém a chave já gravada.
 */
public record ProvedorIaRequest(
        @NotBlank @Size(max = 50) String nome,
        @NotNull TipoProvedorIa tipo,
        @NotBlank @Size(max = 100) String modelo,
        @Size(max = 255) @Pattern(regexp = "^(https?://.+)?$", message = "URL base deve começar com http:// ou https://")
        String baseUrl,
        @Size(max = 500) String apiKey,
        @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperatura,
        @Min(1) @Max(200000) Integer maxTokens,
        @NotNull @Min(5) @Max(300) Integer timeoutSegundos,
        boolean padrao,
        boolean ativo) {
}
