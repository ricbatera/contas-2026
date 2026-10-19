package br.com.rdrtech.cartao;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Regras puras (sem banco) para achar o cartão pelo final lido num documento (fatura ou
 * comprovante) e para calcular em que fatura uma compra cai.
 */
public final class IdentificacaoCartao {

    private IdentificacaoCartao() {
    }

    /**
     * Final do cartão lido pela IA: os 4 últimos dígitos, ou só 3 quando o documento mascara um
     * deles (ex.: "*117"). O cartão é casado depois por sufixo.
     */
    public static String normalizarDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.length() < 3 ? null : digitos.substring(Math.max(0, digitos.length() - 4));
    }

    public static boolean terminaEm(Cartao cartao, String finalLido) {
        return cartao.getUltimosDigitos() != null && cartao.getUltimosDigitos().endsWith(finalLido);
    }

    public static Cartao principalDe(Cartao cartao) {
        return cartao.getCartaoPrincipal() == null ? cartao : cartao.getCartaoPrincipal();
    }

    /**
     * Escolhe o cartão de um comprovante. Entre os que terminam no final lido, ficam os ativos
     * (se houver) e, persistindo o empate, os da bandeira lida. Sem um candidato único, devolve
     * vazio e o usuário escolhe.
     */
    public static Optional<Cartao> escolher(List<Cartao> cartoes, String digitosLidos, Bandeira bandeira) {
        String finalLido = normalizarDigitos(digitosLidos);
        if (finalLido == null) {
            return Optional.empty();
        }
        List<Cartao> candidatos = cartoes.stream().filter(cartao -> terminaEm(cartao, finalLido)).toList();
        List<Cartao> ativos = candidatos.stream().filter(Cartao::isAtivo).toList();
        if (!ativos.isEmpty()) {
            candidatos = ativos;
        }
        if (candidatos.size() > 1 && bandeira != null) {
            List<Cartao> mesmaBandeira = candidatos.stream().filter(cartao -> cartao.getBandeira() == bandeira).toList();
            if (!mesmaBandeira.isEmpty()) {
                candidatos = mesmaBandeira;
            }
        }
        return candidatos.size() == 1 ? Optional.of(candidatos.getFirst()) : Optional.empty();
    }

    /**
     * Vencimento da fatura em que a compra entra. A fatura fecha no {@code diaFechamento}: compras
     * desse dia em diante vão para a fatura seguinte. O vencimento cai no mesmo mês do fechamento
     * quando o dia de vencimento é maior que o de fechamento; senão, no mês seguinte. Dias além do
     * fim do mês (ex.: 31 em fevereiro) viram o último dia.
     */
    public static LocalDate primeiroVencimento(LocalDate dataCompra, int diaFechamento, int diaVencimento) {
        YearMonth mesCompra = YearMonth.from(dataCompra);
        YearMonth mesFechamento = dataCompra.getDayOfMonth() < diaNoMes(mesCompra, diaFechamento)
                ? mesCompra
                : mesCompra.plusMonths(1);
        YearMonth mesVencimento = diaVencimento > diaFechamento ? mesFechamento : mesFechamento.plusMonths(1);
        return mesVencimento.atDay(diaNoMes(mesVencimento, diaVencimento));
    }

    /** Sem os dias no cartão: supõe o vencimento no mês seguinte à compra, no dia informado. */
    public static LocalDate vencimentoNoMesSeguinte(LocalDate dataCompra, int diaVencimento) {
        YearMonth mes = YearMonth.from(dataCompra).plusMonths(1);
        return mes.atDay(diaNoMes(mes, diaVencimento));
    }

    private static int diaNoMes(YearMonth mes, int dia) {
        return Math.min(dia, mes.lengthOfMonth());
    }
}
