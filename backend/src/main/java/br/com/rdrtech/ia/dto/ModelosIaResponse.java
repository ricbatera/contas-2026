package br.com.rdrtech.ia.dto;

import java.util.List;

/**
 * Modelos disponíveis no provedor. Falhas do provedor (chave inválida, URL inacessível, timeout)
 * voltam com a lista vazia e a mensagem em {@code erro}.
 */
public record ModelosIaResponse(List<ModeloIaResponse> modelos, String erro) {
}
