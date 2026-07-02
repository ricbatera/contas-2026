package br.com.rdrtech.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 100) String sobrenome,
        @Size(max = 20) String telefone) {
}
