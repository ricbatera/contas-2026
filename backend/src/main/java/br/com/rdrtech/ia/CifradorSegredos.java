package br.com.rdrtech.ia;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Cifra segredos (API keys) com AES-256-GCM. O texto gravado é {@code v1:} + base64(IV ‖ cifrado),
 * com IV aleatório de 12 bytes por valor; o prefixo de versão permite trocar o algoritmo depois.
 */
@Component
public class CifradorSegredos {

    private static final String PREFIXO = "v1:";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final SecretKey chave;
    private final SecureRandom random = new SecureRandom();

    public CifradorSegredos(IaProperties iaProperties) {
        this.chave = new SecretKeySpec(iaProperties.chaveBytes(), "AES");
    }

    public String cifrar(String textoPlano) {
        try {
            byte[] iv = new byte[TAMANHO_IV];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            byte[] saida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIXO + Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar segredo", e);
        }
    }

    public String decifrar(String textoCifrado) {
        if (textoCifrado == null || !textoCifrado.startsWith(PREFIXO)) {
            throw new IllegalStateException("Formato de segredo cifrado desconhecido");
        }
        try {
            byte[] entrada = Base64.getDecoder().decode(textoCifrado.substring(PREFIXO.length()));
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, entrada, 0, TAMANHO_IV));
            byte[] plano = cipher.doFinal(entrada, TAMANHO_IV, entrada.length - TAMANHO_IV);
            return new String(plano, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Falha ao decifrar segredo; verifique se ia.chave-criptografia mudou", e);
        }
    }
}
