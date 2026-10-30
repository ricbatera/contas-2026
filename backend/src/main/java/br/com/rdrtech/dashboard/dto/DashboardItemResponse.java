package br.com.rdrtech.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code situacao} só vem nas contas fixas: {@code GERADA} (já tem saída no mês) ou {@code PREVISTA}. */
public record DashboardItemResponse(
        String descricao,
        String categoriaNome,
        String subcategoriaNome,
        LocalDate vencimento,
        Integer numeroParcela,
        Integer quantidadeParcelas,
        BigDecimal valor,
        String situacao) {

    public static final String GERADA = "GERADA";
    public static final String PREVISTA = "PREVISTA";

    public DashboardItemResponse(
            String descricao,
            String categoriaNome,
            String subcategoriaNome,
            LocalDate vencimento,
            Integer numeroParcela,
            Integer quantidadeParcelas,
            BigDecimal valor) {
        this(descricao, categoriaNome, subcategoriaNome, vencimento, numeroParcela, quantidadeParcelas, valor, null);
    }
}
