package br.com.rdrtech.faturaimportacao.dto;

import br.com.rdrtech.faturaimportacao.StatusImportacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FaturaImportacaoListaResponse(
        UUID id,
        String nomeArquivo,
        StatusImportacao status,
        UUID cartaoPrincipalId,
        String cartaoNome,
        String cartaoUltimosDigitos,
        Integer competencia,
        LocalDate vencimento,
        BigDecimal totalPdf,
        long preSaidasPendentes,
        LocalDateTime criadoEm) {
}
