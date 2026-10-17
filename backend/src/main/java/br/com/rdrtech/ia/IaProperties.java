package br.com.rdrtech.ia;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ia")
public record IaProperties(String chaveCriptografia) {

    public IaProperties {
        if (chaveCriptografia == null || decodificar(chaveCriptografia).length != 32) {
            throw new IllegalStateException(
                    "ia.chave-criptografia deve ser uma chave AES-256 em base64 (32 bytes). Gere com: openssl rand -base64 32");
        }
    }

    public byte[] chaveBytes() {
        return decodificar(chaveCriptografia);
    }

    private static byte[] decodificar(String valor) {
        try {
            return Base64.getDecoder().decode(valor.trim());
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
