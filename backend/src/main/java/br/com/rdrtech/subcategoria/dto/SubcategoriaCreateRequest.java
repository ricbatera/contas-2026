package br.com.rdrtech.subcategoria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubcategoriaCreateRequest(
        @NotBlank @Size(max = 100) String nome,
        Boolean ativo,
        Boolean padrao) {

    public SubcategoriaCreateRequest(String nome, Boolean ativo) {
        this(nome, ativo, false);
    }
}
