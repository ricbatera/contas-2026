package br.com.rdrtech.presaida.dto;

import br.com.rdrtech.presaida.StatusPreSaida;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PreSaidaResponse(
        UUID id,
        UUID importacaoId,
        UUID cartaoId,
        String cartaoNome,
        String cartaoUltimosDigitos,
        String descricao,
        String descricaoFatura,
        LocalDate dataCompra,
        BigDecimal valorPorParcela,
        int numeroParcela,
        int quantidadeParcelas,
        int competencia,
        LocalDate primeiroVencimento,
        UUID categoriaId,
        String categoriaNome,
        UUID subcategoriaId,
        String subcategoriaNome,
        StatusPreSaida status,
        UUID saidaId) {
}
