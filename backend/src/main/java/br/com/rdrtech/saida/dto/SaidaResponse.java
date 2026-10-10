package br.com.rdrtech.saida.dto;

import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SaidaResponse(
        UUID id,
        String descricao,
        String descricaoFatura,
        BigDecimal valorPorParcela,
        BigDecimal valorTotal,
        int quantidadeParcelas,
        FormaPagamento formaPagamento,
        ModoResponsabilidade modoResponsabilidade,
        LocalDate dataInicial,
        LocalDate primeiroVencimento,
        UUID cartaoId,
        String cartaoNome,
        UUID categoriaId,
        String categoriaNome,
        UUID subcategoriaId,
        String subcategoriaNome,
        UUID responsavelPessoaId,
        List<SaidaResponsavelResponse> responsabilidades,
        List<SaidaParcelaResponse> parcelas,
        LocalDateTime criadoEm,
        boolean contaFixa) {
}
