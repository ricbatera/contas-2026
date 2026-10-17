package br.com.rdrtech.faturaimportacao.dto;

import br.com.rdrtech.faturaimportacao.StatusImportacao;
import java.time.LocalDateTime;
import java.util.UUID;

/** Situação resumida da importação, consultada periodicamente pelo frontend enquanto processa. */
public record FaturaImportacaoStatusResponse(
        UUID id,
        String nomeArquivo,
        StatusImportacao status,
        String erro,
        long preSaidasPendentes,
        LocalDateTime atualizadoEm) {
}
