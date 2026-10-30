package br.com.rdrtech.dashboard.dto;

import java.math.BigDecimal;

public record DashboardProjectionMonthResponse(
        int mes,
        int ano,
        BigDecimal gastosLancados,
        BigDecimal bucketsReservados,
        BigDecimal contasFixas,
        BigDecimal contasFixasPrevistas,
        BigDecimal totalPrevisto,
        BigDecimal receitasPrevistas,
        BigDecimal saldoPrevisto) {
}
