package br.com.rdrtech.saida.dto;

import java.util.List;

/** {@code ignoradas} conta os itens cuja série já tinha saída no mês. */
public record GerarContasFixasResponse(List<SaidaResponse> criadas, int ignoradas) {
}
