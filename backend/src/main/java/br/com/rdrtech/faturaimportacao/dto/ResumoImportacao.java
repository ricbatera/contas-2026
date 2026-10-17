package br.com.rdrtech.faturaimportacao.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Resultado da conciliação de uma fatura importada, gravado como JSON na importação. */
public record ResumoImportacao(
        List<ItemConciliado> conciliados,
        List<ItemIgnorado> ignorados,
        List<String> avisos,
        int preSaidasCriadas,
        BigDecimal totalSistema,
        BigDecimal totalPreSaidas) {

    public ResumoImportacao {
        conciliados = conciliados == null ? List.of() : List.copyOf(conciliados);
        ignorados = ignorados == null ? List.of() : List.copyOf(ignorados);
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }

    /** Item do PDF que já existia como parcela de uma saída. */
    public record ItemConciliado(
            String descricaoPdf,
            String ultimosDigitos,
            BigDecimal valor,
            int numeroParcela,
            int quantidadeParcelas,
            UUID saidaId,
            String descricaoSaida,
            String descricaoFaturaAnterior) {
    }

    /** Item do PDF que não gera saída (pagamento, estorno, crédito ou dado inválido). */
    public record ItemIgnorado(String descricao, BigDecimal valor, String tipo, String motivo) {
    }
}
