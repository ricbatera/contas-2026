package br.com.rdrtech.categoria.dto;

import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.subcategoria.dto.SubcategoriaResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CategoriaResponse(
        UUID id,
        String nome,
        TipoCategoria tipo,
        boolean ativo,
        boolean padrao,
        LocalDateTime criadoEm,
        List<SubcategoriaResponse> subcategorias) {
}
