package br.com.rdrtech.faturaimportacao.dto;

import br.com.rdrtech.faturaimportacao.StatusImportacao;
import br.com.rdrtech.presaida.dto.PreSaidaResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FaturaImportacaoResponse(
        UUID id,
        String nomeArquivo,
        int tamanhoBytes,
        StatusImportacao status,
        String erro,
        UUID cartaoPrincipalId,
        String cartaoNome,
        String cartaoUltimosDigitos,
        Integer competencia,
        LocalDate vencimento,
        BigDecimal totalPdf,
        String provedorModelo,
        LocalDateTime criadoEm,
        ResumoImportacao resumo,
        List<PreSaidaResponse> preSaidas) {
}
