package br.com.rdrtech.fatura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FaturaResponse(
        UUID cartaoId,
        String cartaoNome,
        String banco,
        int competencia,
        LocalDate vencimento,
        BigDecimal total,
        List<FaturaParcelaResponse> parcelas) {
}
