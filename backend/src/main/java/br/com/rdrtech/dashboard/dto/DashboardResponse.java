package br.com.rdrtech.dashboard.dto;

import java.math.BigDecimal;

public record DashboardResponse(
        int mes,
        int ano,
        BigDecimal gastosLancados,
        BigDecimal bucketsReservados,
        BigDecimal contasFixasMesAnterior,
        BigDecimal totalPrevisto) {
}
