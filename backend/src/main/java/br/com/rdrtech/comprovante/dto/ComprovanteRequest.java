package br.com.rdrtech.comprovante.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Foto do comprovante em base64 (aceita o prefixo {@code data:image/...;base64,}). */
public record ComprovanteRequest(
        @NotBlank @Size(max = 255) String nomeArquivo,
        @NotBlank String conteudoBase64) {
}
