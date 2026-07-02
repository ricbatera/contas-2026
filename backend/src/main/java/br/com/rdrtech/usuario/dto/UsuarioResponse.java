package br.com.rdrtech.usuario.dto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        String nome,
        String sobrenome,
        String email,
        String telefone,
        Set<String> roles,
        LocalDateTime criadoEm) {
}
