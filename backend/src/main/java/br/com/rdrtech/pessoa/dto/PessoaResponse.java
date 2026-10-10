package br.com.rdrtech.pessoa.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PessoaResponse(
        UUID id,
        String nome,
        boolean ativo,
        LocalDateTime criadoEm) {
}
