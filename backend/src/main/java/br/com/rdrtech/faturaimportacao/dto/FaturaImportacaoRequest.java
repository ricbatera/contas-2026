package br.com.rdrtech.faturaimportacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PDF da fatura em base64 (com ou sem o prefixo {@code data:application/pdf;base64,}). A senha
 * só é usada para abrir o arquivo e não é gravada.
 */
public record FaturaImportacaoRequest(
        @NotBlank @Size(max = 255) String nomeArquivo,
        @NotBlank String conteudoBase64,
        @Size(max = 100) String senha) {
}
