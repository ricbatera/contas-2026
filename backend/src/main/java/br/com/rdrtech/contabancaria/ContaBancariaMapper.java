package br.com.rdrtech.contabancaria;

import br.com.rdrtech.contabancaria.dto.ContaBancariaResponse;

public final class ContaBancariaMapper {

    private ContaBancariaMapper() {
    }

    public static ContaBancariaResponse toResponse(ContaBancaria contaBancaria) {
        return new ContaBancariaResponse(
                contaBancaria.getId(),
                contaBancaria.getNome(),
                contaBancaria.getBanco(),
                contaBancaria.getTipoConta(),
                contaBancaria.getAgencia(),
                contaBancaria.getNumeroConta(),
                contaBancaria.getSaldoInicial(),
                contaBancaria.getCor(),
                contaBancaria.isAtivo(),
                contaBancaria.getAuditoria().getCreatedAt());
    }
}
