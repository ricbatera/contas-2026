package br.com.rdrtech.saida.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaidaResponsavelResponse(
        UUID pessoaId,
        UUID usuarioId,
        String nome,
        BigDecimal valorPorParcela) {
}
