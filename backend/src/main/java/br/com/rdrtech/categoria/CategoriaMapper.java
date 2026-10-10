package br.com.rdrtech.categoria;

import br.com.rdrtech.categoria.dto.CategoriaResponse;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.dto.SubcategoriaResponse;
import java.util.Comparator;

public final class CategoriaMapper {

    private CategoriaMapper() {
    }

    public static boolean isPadrao(Categoria categoria) {
        return categoria.getSubcategorias().stream().anyMatch(Subcategoria::isPadrao);
    }

    public static CategoriaResponse toResponse(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getId(),
                categoria.getNome(),
                categoria.getTipo(),
                categoria.isAtivo(),
                isPadrao(categoria),
                categoria.getAuditoria() != null ? categoria.getAuditoria().getCreatedAt() : null,
                categoria.getSubcategorias().stream()
                        .sorted(Comparator.comparing(Subcategoria::getNome, String.CASE_INSENSITIVE_ORDER))
                        .map(CategoriaMapper::toSubcategoriaResponse)
                        .toList());
    }

    public static SubcategoriaResponse toSubcategoriaResponse(Subcategoria subcategoria) {
        return new SubcategoriaResponse(
                subcategoria.getId(),
                subcategoria.getNome(),
                subcategoria.isAtivo(),
                subcategoria.isPadrao(),
                subcategoria.getCategoria().getId(),
                subcategoria.getCategoria().getNome(),
                subcategoria.getAuditoria() != null ? subcategoria.getAuditoria().getCreatedAt() : null);
    }
}
