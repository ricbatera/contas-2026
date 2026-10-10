package br.com.rdrtech.subcategoria.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubcategoriaResponse(
        UUID id,
        String nome,
        boolean ativo,
        boolean padrao,
        UUID categoriaId,
        String categoriaNome,
        LocalDateTime criadoEm) {
}
