package br.com.rdrtech.dashboard.dto;

import java.math.BigDecimal;

public record DashboardMonthlyAmountResponse(int ano, int mes, BigDecimal valor) {
}
