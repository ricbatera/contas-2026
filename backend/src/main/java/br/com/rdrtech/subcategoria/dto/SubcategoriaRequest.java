package br.com.rdrtech.subcategoria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubcategoriaRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull UUID categoriaId,
        Boolean ativo,
        Boolean padrao) {

    public SubcategoriaRequest(String nome, UUID categoriaId, Boolean ativo) {
        this(nome, categoriaId, ativo, null);
    }
}
