package br.com.rdrtech.ia.dto;

/**
 * Resultado de uma chamada de teste ao provedor. Falhas do provedor (chave inválida, modelo
 * inexistente, timeout) voltam com {@code sucesso=false} e a mensagem em {@code erro}.
 */
public record TesteIaResponse(
        boolean sucesso,
        String prompt,
        String resposta,
        String modelo,
        long latenciaMs,
        Integer tokensEntrada,
        Integer tokensSaida,
        String erro) {
}
