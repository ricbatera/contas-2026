package br.com.rdrtech.categoria.dto;

import br.com.rdrtech.categoria.TipoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull TipoCategoria tipo,
        Boolean ativo) {
}
