package br.com.rdrtech.ia.dto;

import br.com.rdrtech.ia.TipoProvedorIa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Dados do formulário para consultar os modelos do provedor. Sem {@code apiKey}, usa a chave já
 * gravada no provedor {@code provedorId} (edição).
 */
public record ModelosIaRequest(
        @NotNull TipoProvedorIa tipo,
        @Size(max = 255) @Pattern(regexp = "^(https?://.+)?$", message = "URL base deve começar com http:// ou https://")
        String baseUrl,
        @Size(max = 500) String apiKey,
        UUID provedorId) {
}
