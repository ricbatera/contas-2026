package br.com.rdrtech.presaida;

import br.com.rdrtech.presaida.dto.PreSaidaResponse;

public final class PreSaidaMapper {

    private PreSaidaMapper() {
    }

    public static PreSaidaResponse toResponse(PreSaida preSaida) {
        var cartao = preSaida.getCartao();
        var categoria = preSaida.getCategoria();
        var subcategoria = preSaida.getSubcategoria();
        var sugerida = preSaida.getSaidaSugerida();
        return new PreSaidaResponse(
                preSaida.getId(),
                preSaida.getImportacao().getId(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                cartao != null ? cartao.getUltimosDigitos() : null,
                preSaida.getDescricao(),
                preSaida.getDescricaoFatura(),
                preSaida.getDataCompra(),
                preSaida.getValorPorParcela(),
                preSaida.getNumeroParcela(),
                preSaida.getQuantidadeParcelas(),
                preSaida.getCompetencia(),
                preSaida.getPrimeiroVencimento(),
                categoria != null ? categoria.getId() : null,
                categoria != null ? categoria.getNome() : null,
                subcategoria != null ? subcategoria.getId() : null,
                subcategoria != null ? subcategoria.getNome() : null,
                preSaida.getStatus(),
                preSaida.getSaida() != null ? preSaida.getSaida().getId() : null,
                sugerida != null ? sugerida.getId() : null,
                sugerida != null ? sugerida.getDescricao() : null,
                sugerida != null ? sugerida.getDescricaoFatura() : null,
                preSaida.getMotivoSugestao());
    }
}
