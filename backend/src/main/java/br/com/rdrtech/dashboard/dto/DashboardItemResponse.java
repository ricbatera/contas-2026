package br.com.rdrtech.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardItemResponse(
        String descricao,
        String categoriaNome,
        String subcategoriaNome,
        LocalDate vencimento,
        Integer numeroParcela,
        Integer quantidadeParcelas,
        BigDecimal valor) {
}
