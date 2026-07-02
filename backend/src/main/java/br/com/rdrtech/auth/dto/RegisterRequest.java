package br.com.rdrtech.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 100) String sobrenome,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$",
                message = "A senha deve ter no mínimo 8 caracteres, 1 número e 1 caractere especial")
        @Schema(example = "Senha123!")
        String senha,
        @Size(max = 20) String telefone) {
}
