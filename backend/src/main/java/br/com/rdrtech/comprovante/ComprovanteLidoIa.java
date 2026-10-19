package br.com.rdrtech.comprovante;

import java.math.BigDecimal;

/**
 * Estrutura que a IA devolve ao ler a foto do comprovante. Datas, enums e ids chegam como texto
 * e são convertidos com tolerância, para um valor mal formatado não invalidar a leitura inteira.
 */
public record ComprovanteLidoIa(
        Boolean legivel,
        String estabelecimento,
        String data,
        BigDecimal valorTotal,
        String modalidade,
        Integer parcelas,
        String ultimosDigitos,
        String bandeira,
        String categoriaId,
        String subcategoriaId) {
}
