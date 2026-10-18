package br.com.rdrtech.entrada.dto;

import br.com.rdrtech.entrada.StatusOcorrencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EntradaOcorrenciaResponse(
        UUID id,
        UUID entradaId,
        String descricao,
        boolean recorrente,
        UUID categoriaId,
        String categoriaNome,
        UUID subcategoriaId,
        String subcategoriaNome,
        UUID contaBancariaId,
        String contaBancariaNome,
        LocalDate competencia,
        LocalDate dataPrevista,
        BigDecimal valorPrevisto,
        StatusOcorrencia status,
        BigDecimal valorRecebido,
        LocalDate dataRecebimento) {
}
