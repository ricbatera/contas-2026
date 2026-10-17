package br.com.rdrtech.faturaimportacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A fila de processamento fica em memória: importações que estavam PROCESSANDO quando a
 * aplicação parou nunca terminariam. Na subida elas passam a ERRO para o usuário reprocessar.
 * Usa SQL direto porque não há usuário autenticado para o filtro de tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecuperacaoImportacoesInterrompidas {

    static final String MENSAGEM = "O processamento foi interrompido (o servidor reiniciou). Reprocesse a importação.";

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void marcarInterrompidas() {
        int alteradas = jdbcTemplate.update(
                "update fatura_importacao set status = 'ERRO', erro = ? where status = 'PROCESSANDO'", MENSAGEM);
        if (alteradas > 0) {
            log.warn("{} importação(ões) de fatura interrompida(s) marcada(s) como ERRO", alteradas);
        }
    }
}
