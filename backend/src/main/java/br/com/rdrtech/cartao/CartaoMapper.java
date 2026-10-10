package br.com.rdrtech.cartao;

import br.com.rdrtech.cartao.dto.CartaoResponse;

public final class CartaoMapper {

    private CartaoMapper() {
    }

    public static CartaoResponse toResponse(Cartao cartao) {
        return new CartaoResponse(
                cartao.getId(),
                cartao.getNome(),
                cartao.getBanco(),
                cartao.getBandeira(),
                cartao.getUltimosDigitos(),
                cartao.getCor(),
                cartao.getAuditoria() != null ? cartao.getAuditoria().getCreatedAt() : null,
                cartao.getTipo(),
                cartao.isVirtual(),
                cartao.getCartaoPrincipal() != null ? cartao.getCartaoPrincipal().getId() : null,
                cartao.isAtivo());
    }
}
