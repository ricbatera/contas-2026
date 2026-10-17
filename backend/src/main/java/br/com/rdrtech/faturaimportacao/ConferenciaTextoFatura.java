package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.faturaimportacao.FaturaLidaIa.ItemLido;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Confere a leitura da IA contra o texto extraído do PDF, sem IA: conta as linhas que parecem
 * lançamentos (para avisar quando a IA deixou itens de fora) e corrige o parcelamento a partir da
 * própria linha do item. Faturas costumam trazer marcadores antes da data (ex.: "3 23/09 LOJA") que
 * a IA confunde com parcela; a parcela de verdade vem depois da descrição ("LOJA 07/10 292,23").
 */
final class ConferenciaTextoFatura {

    /** Linha com data dd/mm, descrição e valor em reais no fim (opcionalmente seguido do valor em US$). */
    private static final Pattern LANCAMENTO = Pattern.compile(
            "(?:^|\\s)\\d{2}/\\d{2}\\s+\\S.*?\\s-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}(?:\\s+\\d{1,3}(?:\\.\\d{3})*,\\d{2})?\\s*$");
    private static final Pattern PARCELA = Pattern.compile("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])");

    private ConferenciaTextoFatura() {
    }

    static int contarLancamentos(String texto) {
        return (int) texto.lines().filter(linha -> LANCAMENTO.matcher(linha).find()).count();
    }

    static boolean temLancamentos(String texto) {
        return texto.lines().anyMatch(linha -> LANCAMENTO.matcher(linha).find());
    }

    /**
     * Divide o texto em trechos com no máximo {@code maximo} linhas de lançamento, sempre entre
     * linhas. Linhas que não são lançamento (cabeçalhos de cartão e de seção) seguem no trecho atual.
     */
    static List<String> dividir(String texto, int maximo) {
        List<String> trechos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        int lancamentos = 0;
        for (String linha : texto.lines().toList()) {
            boolean ehLancamento = LANCAMENTO.matcher(linha).find();
            if (ehLancamento && lancamentos == maximo) {
                trechos.add(atual.toString());
                atual.setLength(0);
                lancamentos = 0;
            }
            atual.append(linha).append('\n');
            if (ehLancamento) {
                lancamentos++;
            }
        }
        if (!atual.isEmpty() || trechos.isEmpty()) {
            trechos.add(atual.toString());
        }
        return trechos;
    }

    /**
     * Para cada item, procura no texto a linha com a descrição (de preferência também com o valor)
     * e usa a parcela "NN/NN" que aparece da descrição em diante. Sem parcela na linha, é à vista.
     * Itens cuja descrição não está no texto ficam como a IA leu.
     */
    static List<ItemLido> corrigirParcelas(List<ItemLido> itens, String texto) {
        List<String> linhas = texto.lines().map(ConferenciaTextoFatura::normalizar).toList();
        List<ItemLido> corrigidos = new ArrayList<>(itens.size());
        for (ItemLido item : itens) {
            corrigidos.add(corrigir(item, linhas));
        }
        return corrigidos;
    }

    private static ItemLido corrigir(ItemLido item, List<String> linhas) {
        String descricao = item.descricao() == null ? "" : normalizar(item.descricao());
        if (descricao.length() < 3) {
            return item;
        }
        String valor = item.valor() == null ? null : formatar(item.valor().abs());
        String linha = linhas.stream()
                .filter(l -> l.contains(descricao) && valor != null && l.contains(valor))
                .findFirst()
                .orElseGet(() -> linhas.stream().filter(l -> l.contains(descricao)).findFirst().orElse(null));
        if (linha == null) {
            return item;
        }
        Matcher matcher = PARCELA.matcher(linha.substring(linha.indexOf(descricao)));
        int atual = 1;
        int total = 1;
        while (matcher.find()) {
            int a = Integer.parseInt(matcher.group(1));
            int t = Integer.parseInt(matcher.group(2));
            if (a >= 1 && t >= a && t <= 60 && t > 1) {
                atual = a;
                total = t;
                break;
            }
        }
        if (item.parcelaAtual() != null && item.totalParcelas() != null
                && item.parcelaAtual() == atual && item.totalParcelas() == total) {
            return item;
        }
        return new ItemLido(item.ultimosDigitos(), item.data(), item.descricao(), item.valor(), atual, total,
                item.tipo(), item.categoriaId(), item.subcategoriaId());
    }

    private static String formatar(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return formato.format(valor);
    }

    private static String normalizar(String texto) {
        return texto.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
