package br.com.rdrtech.pessoa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PessoaRequest(
        @NotBlank @Size(max = 100) String nome,
        Boolean ativo) {
}
