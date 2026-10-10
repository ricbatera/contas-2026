package br.com.rdrtech.fatura.dto;

import br.com.rdrtech.saida.dto.SaidaResponsavelResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FaturaParcelaResponse(
        UUID saidaId,
        String descricao,
        String descricaoFatura,
        UUID cartaoId,
        String cartaoNome,
        int numeroParcela,
        int quantidadeParcelas,
        LocalDate vencimento,
        BigDecimal valor,
        List<SaidaResponsavelResponse> responsaveis) {
}
