package br.com.rdrtech.bucket.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BucketResponse(
        UUID id,
        UUID categoriaId,
        String categoriaNome,
        UUID subcategoriaId,
        String subcategoriaNome,
        UUID responsavelPessoaId,
        String responsavelNome,
        boolean responsavelUsuario,
        BigDecimal valorMensal,
        BigDecimal valorConsumido,
        BigDecimal saldo,
        BigDecimal percentualConsumido,
        int mes,
        int ano) {
}
