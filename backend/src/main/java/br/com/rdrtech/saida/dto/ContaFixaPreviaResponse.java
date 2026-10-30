package br.com.rdrtech.saida.dto;

import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Conta fixa proposta para o mês, ainda não gravada; {@code aviso} indica que ela não pode ser gerada como está. */
public record ContaFixaPreviaResponse(
        UUID serieId,
        UUID saidaOrigemId,
        String descricao,
        String categoriaNome,
        String subcategoriaNome,
        FormaPagamento formaPagamento,
        ModoResponsabilidade modoResponsabilidade,
        List<String> responsaveis,
        BigDecimal valorPorParcela,
        LocalDate dataSugerida,
        String aviso) {
}
