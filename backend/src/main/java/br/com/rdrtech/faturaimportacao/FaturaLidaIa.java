package br.com.rdrtech.faturaimportacao;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estrutura que a IA devolve ao ler a fatura. Datas e ids chegam como texto e são convertidos
 * com tolerância pela conciliação, para um valor mal formatado não invalidar a leitura inteira.
 */
public record FaturaLidaIa(String vencimento, BigDecimal total, List<CartaoLido> cartoes, List<ItemLido> itens) {

    public FaturaLidaIa {
        cartoes = cartoes == null ? List.of() : cartoes;
        itens = itens == null ? List.of() : itens;
    }

    public record CartaoLido(String ultimosDigitos, String titular) {
    }

    public record ItemLido(
            String ultimosDigitos,
            String data,
            String descricao,
            BigDecimal valor,
            Integer parcelaAtual,
            Integer totalParcelas,
            String tipo,
            String categoriaId,
            String subcategoriaId) {
    }
}
