package br.com.rdrtech.fatura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FaturaCompetenciaResponse(int competencia, LocalDate vencimento, BigDecimal total) {
}
