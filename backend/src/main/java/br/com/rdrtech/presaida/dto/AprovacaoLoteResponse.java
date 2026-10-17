package br.com.rdrtech.presaida.dto;

import java.util.List;
import java.util.UUID;

/** Cada pré-saída é aprovada em transação própria: uma falha não desfaz as demais. */
public record AprovacaoLoteResponse(List<PreSaidaResponse> aprovadas, List<Falha> falhas) {

    public record Falha(UUID id, String mensagem) {
    }
}
