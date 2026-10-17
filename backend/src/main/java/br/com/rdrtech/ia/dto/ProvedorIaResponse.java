package br.com.rdrtech.ia.dto;

import br.com.rdrtech.ia.TipoProvedorIa;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProvedorIaResponse(
        UUID id,
        String nome,
        TipoProvedorIa tipo,
        String modelo,
        String baseUrl,
        boolean possuiApiKey,
        String apiKeyMascarada,
        BigDecimal temperatura,
        Integer maxTokens,
        int timeoutSegundos,
        boolean padrao,
        boolean ativo,
        LocalDateTime criadoEm) {
}
