package br.com.rdrtech.saida.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SaidaParcelaResponse(
        UUID id,
        int numero,
        BigDecimal valor,
        LocalDate vencimento,
        UUID faturaId,
        LocalDate vencimentoFatura,
        BigDecimal totalFatura) {
}
