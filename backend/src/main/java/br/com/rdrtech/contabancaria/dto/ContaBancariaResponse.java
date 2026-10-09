package br.com.rdrtech.contabancaria.dto;

import br.com.rdrtech.contabancaria.TipoConta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContaBancariaResponse(
        UUID id,
        String nome,
        String banco,
        TipoConta tipoConta,
        String agencia,
        String numeroConta,
        BigDecimal saldoInicial,
        String cor,
        boolean ativo,
        LocalDateTime criadoEm) {
}
