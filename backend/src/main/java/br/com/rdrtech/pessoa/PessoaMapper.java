package br.com.rdrtech.pessoa;

import br.com.rdrtech.pessoa.dto.PessoaResponse;

public final class PessoaMapper {

    private PessoaMapper() {
    }

    public static PessoaResponse toResponse(Pessoa pessoa) {
        return new PessoaResponse(
                pessoa.getId(),
                pessoa.getNome(),
                pessoa.isAtivo(),
                pessoa.getAuditoria() != null ? pessoa.getAuditoria().getCreatedAt() : null);
    }
}
