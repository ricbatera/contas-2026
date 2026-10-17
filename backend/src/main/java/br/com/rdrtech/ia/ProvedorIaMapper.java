package br.com.rdrtech.ia;

import br.com.rdrtech.ia.dto.ProvedorIaResponse;

public final class ProvedorIaMapper {

    private ProvedorIaMapper() {
    }

    public static ProvedorIaResponse toResponse(ProvedorIa provedor, String apiKeyPlana) {
        return new ProvedorIaResponse(
                provedor.getId(),
                provedor.getNome(),
                provedor.getTipo(),
                provedor.getModelo(),
                provedor.getBaseUrl(),
                apiKeyPlana != null,
                mascarar(apiKeyPlana),
                provedor.getTemperatura(),
                provedor.getMaxTokens(),
                provedor.getTimeoutSegundos(),
                provedor.isPadrao(),
                provedor.isAtivo(),
                provedor.getAuditoria().getCreatedAt());
    }

    /** Mostra só o início e o fim da chave, ex.: {@code sk-...a1b2}. */
    static String mascarar(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        if (apiKey.length() <= 10) {
            return "****";
        }
        return apiKey.substring(0, 3) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
