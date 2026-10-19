package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.presaida.StatusPreSaida;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Regras de conciliação entre os itens lidos da fatura e as parcelas já lançadas, sem acesso a
 * banco: recebe retratos dos dados e devolve uma decisão por item. Etapas, cada uma sobre todos os
 * itens antes da próxima:
 *
 * <ol>
 *   <li>antes de tudo, decisões do usuário (pré-saída vinculada, rejeitada ou com sugestão recusada);</li>
 *   <li>exata: mesmo valor, número e quantidade de parcelas na fatura da competência, sem conflito
 *       com a descrição de fatura já gravada. Empate entre saídas diferentes fica para sugestão;</li>
 *   <li>depois da exata, pré-saída pendente de outra importação com a mesma chave;</li>
 *   <li>por valor: um único item e uma única parcela livre com aquele valor, e a parcela ainda sem
 *       descrição de fatura (ou com descrição parecida). Cobre saídas lançadas à mão com um apelido
 *       e numeração diferente da fatura (ex.: assinatura 8/12 no sistema, 1/1 no PDF);</li>
 *   <li>sugestões: o que sobra vira pré-saída nova, possivelmente apontando uma saída que parece ser
 *       a mesma (indicada pela IA, ou de mesmo valor na competência ou num mês vizinho). Sugestão
 *       nunca vincula sozinha: o usuário confirma.</li>
 * </ol>
 */
final class MotorConciliacao {

    static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    /** Similaridade mínima para aceitar vínculo por valor com uma descrição de fatura já preenchida. */
    static final double SIMILARIDADE_DESCRICAO = 0.5;
    /** Diferença máxima de valor (proporcional) aceita numa sugestão da IA. */
    static final BigDecimal VARIACAO_MAXIMA_IA = new BigDecimal("0.5");

    private MotorConciliacao() {
    }

    /** Item válido do PDF (os ignorados não chegam aqui). {@code indice} é a posição na leitura da IA. */
    record Item(int indice, String descricao, String digitos, UUID cartaoId, BigDecimal valor, int numero,
            int quantidade) {
    }

    record Parcela(UUID parcelaId, UUID saidaId, String descricao, String descricaoFatura, UUID cartaoId,
            String cartaoDigitos, BigDecimal valor, int numero, int quantidade, int competencia, boolean vizinha) {
    }

    /** {@code decididaPeloUsuario}: vinculada, rejeitada ou com sugestão recusada; prevalece sobre o casamento. */
    record PreSaidaExistente(UUID id, StatusPreSaida status, UUID saidaId, String descricao, String descricaoFatura,
            BigDecimal valor, int numero, int quantidade, boolean decididaPeloUsuario) {
    }

    /** Par indicado pela IA entre um item e uma parcela candidata. */
    record ParIa(int itemIndice, UUID parcelaId, String motivo) {
    }

    record Sugestao(Parcela parcela, String motivo) {
    }

    sealed interface Decisao permits Exata, PorValor, Reaproveitada, Nova {
    }

    record Exata(Parcela parcela) implements Decisao {
    }

    record PorValor(Parcela parcela, String observacao) implements Decisao {
    }

    record Reaproveitada(PreSaidaExistente preSaida) implements Decisao {
    }

    record Nova(Sugestao sugestao) implements Decisao {
    }

    /** Itens sem par e parcelas candidatas, enviados à IA para indicar possíveis correspondências. */
    record Desempate(List<Item> itens, List<Parcela> parcelas) {

        boolean necessario() {
            return !itens.isEmpty() && !parcelas.isEmpty();
        }
    }

    record Resultado(Map<Integer, Decisao> decisoes, Desempate desempate, List<Parcela> faltantes) {
    }

    static Resultado conciliar(List<Item> itens, List<Parcela> parcelas, List<PreSaidaExistente> preSaidas,
            List<ParIa> paresIa) {
        Map<Integer, Decisao> decisoes = new LinkedHashMap<>();
        List<Parcela> livres = new ArrayList<>(parcelas.stream().filter(p -> !p.vizinha()).toList());
        List<Parcela> vizinhas = new ArrayList<>(parcelas.stream().filter(Parcela::vizinha).toList());
        List<PreSaidaExistente> preSaidasLivres = new ArrayList<>(preSaidas);

        // 0. Decisões do usuário em importações ou processamentos anteriores valem antes de tudo.
        reaproveitar(itens, decisoes, preSaidasLivres, livres, PreSaidaExistente::decididaPeloUsuario);

        // 1. Exata. Empate entre saídas diferentes (ex.: duas assinaturas de R$ 50 sem descrição de
        // fatura) não é decidido aqui: vira sugestão para o usuário, em vez de um vínculo às cegas.
        for (Item item : pendentes(itens, decisoes)) {
            List<Parcela> candidatas = livres.stream()
                    .filter(p -> mesmoValor(p.valor(), item.valor()) && p.numero() == item.numero()
                            && p.quantidade() == item.quantidade() && descricaoCompativel(item, p))
                    .sorted(Comparator.comparingDouble((Parcela p) -> pontuacao(item, p)).reversed())
                    .toList();
            if (candidatas.isEmpty()) {
                continue;
            }
            Parcela parcela = candidatas.getFirst();
            boolean empate = candidatas.size() > 1
                    && pontuacao(item, candidatas.get(1)) >= pontuacao(item, parcela) - 0.001
                    && !mesmaSaidaAparente(parcela, candidatas.get(1));
            if (!empate) {
                livres.remove(parcela);
                decisoes.put(item.indice(), new Exata(parcela));
            }
        }

        // 2. Pré-saída pendente de outra importação com a mesma chave.
        reaproveitar(itens, decisoes, preSaidasLivres, livres, p -> true);

        // 3. Por valor, quando o par é único e a parcela ainda não tem descrição de fatura conflitante.
        List<Item> semPar = pendentes(itens, decisoes);
        for (Item item : semPar) {
            List<Parcela> candidatas = livres.stream()
                    .filter(p -> mesmoValor(p.valor(), item.valor()) && descricaoCompativel(item, p))
                    .toList();
            if (candidatas.size() != 1) {
                continue;
            }
            Parcela parcela = candidatas.getFirst();
            long itensComValor = semPar.stream()
                    .filter(outro -> !decisoes.containsKey(outro.indice()))
                    .filter(outro -> mesmoValor(outro.valor(), parcela.valor()) && descricaoCompativel(outro, parcela))
                    .count();
            if (itensComValor == 1) {
                livres.remove(parcela);
                decisoes.put(item.indice(), new PorValor(parcela, divergencia(item, parcela)));
            }
        }

        // 4. Sugestões para o que sobrou.
        List<Item> restantes = pendentes(itens, decisoes);
        List<Parcela> candidatasSugestao = new ArrayList<>(livres);
        vizinhas.stream().filter(p -> vizinhaElegivel(p, restantes)).forEach(candidatasSugestao::add);
        Desempate desempate = new Desempate(restantes, List.copyOf(candidatasSugestao));

        Set<UUID> sugeridas = new HashSet<>();
        Map<Integer, Sugestao> sugestoes = new LinkedHashMap<>();
        for (ParIa par : paresIa == null ? List.<ParIa>of() : paresIa) {
            Item item = restantes.stream().filter(i -> i.indice() == par.itemIndice()).findFirst().orElse(null);
            Parcela parcela = candidatasSugestao.stream().filter(p -> p.parcelaId().equals(par.parcelaId()))
                    .findFirst().orElse(null);
            if (item == null || parcela == null || sugestoes.containsKey(item.indice())
                    || !sugeridas.add(parcela.parcelaId()) || !valorAceitavelParaIa(item, parcela, par.motivo())) {
                continue;
            }
            sugestoes.put(item.indice(), new Sugestao(parcela, motivoIa(item, parcela, par.motivo())));
        }
        for (Item item : restantes) {
            if (sugestoes.containsKey(item.indice())) {
                continue;
            }
            Parcela parcela = melhor(candidatasSugestao,
                    p -> !sugeridas.contains(p.parcelaId()) && mesmoValor(p.valor(), item.valor())
                            && (p.vizinha() ? vizinhaCompativel(item, p) : descricaoCompativel(item, p)),
                    p -> pontuacao(item, p) - (p.vizinha() ? 1 : 0));
            if (parcela != null) {
                sugeridas.add(parcela.parcelaId());
                sugestoes.put(item.indice(), new Sugestao(parcela, motivoValor(parcela)));
            }
        }
        restantes.forEach(item -> decisoes.put(item.indice(), new Nova(sugestoes.get(item.indice()))));

        List<Parcela> faltantes = livres.stream().filter(p -> !sugeridas.contains(p.parcelaId())).toList();
        return new Resultado(decisoes, desempate, faltantes);
    }

    private static void reaproveitar(List<Item> itens, Map<Integer, Decisao> decisoes,
            List<PreSaidaExistente> preSaidasLivres, List<Parcela> livres, Predicate<PreSaidaExistente> elegivel) {
        for (Item item : pendentes(itens, decisoes)) {
            PreSaidaExistente existente = melhor(preSaidasLivres,
                    p -> elegivel.test(p) && mesmoValor(p.valor(), item.valor()) && p.numero() == item.numero()
                            && p.quantidade() == item.quantidade(),
                    p -> similaridade(item.descricao(), p.descricaoFatura(), p.descricao()));
            if (existente == null) {
                continue;
            }
            preSaidasLivres.remove(existente);
            decisoes.put(item.indice(), new Reaproveitada(existente));
            if (existente.status() == StatusPreSaida.VINCULADA) {
                // A parcela da saída vinculada já está representada por este item.
                livres.stream().filter(p -> p.saidaId().equals(existente.saidaId())).findFirst()
                        .ifPresent(livres::remove);
            }
        }
    }

    private static List<Item> pendentes(List<Item> itens, Map<Integer, Decisao> decisoes) {
        return itens.stream().filter(item -> !decisoes.containsKey(item.indice())).toList();
    }

    /** Compras idênticas (mesmo apelido e descrição de fatura): tanto faz qual parcela recebe o item. */
    private static boolean mesmaSaidaAparente(Parcela a, Parcela b) {
        return Objects.equals(normalizar(a.descricao()), normalizar(b.descricao()))
                && Objects.equals(normalizar(a.descricaoFatura()), normalizar(b.descricaoFatura()));
    }

    private static String normalizar(String texto) {
        return texto == null || texto.isBlank() ? null : String.join(" ", termos(texto));
    }

    private static boolean descricaoCompativel(Item item, Parcela parcela) {
        return parcela.descricaoFatura() == null || parcela.descricaoFatura().isBlank()
                || similaridade(item.descricao(), parcela.descricaoFatura()) >= SIMILARIDADE_DESCRICAO;
    }

    /** Parcela de mês vizinho só entra se a descrição de fatura estiver vazia ou for a do PDF. */
    private static boolean vizinhaCompativel(Item item, Parcela parcela) {
        return parcela.descricaoFatura() == null || parcela.descricaoFatura().isBlank()
                || similaridade(item.descricao(), parcela.descricaoFatura()) >= 0.8;
    }

    private static boolean vizinhaElegivel(Parcela parcela, List<Item> itens) {
        return itens.stream().anyMatch(item -> vizinhaCompativel(item, parcela));
    }

    private static boolean valorAceitavelParaIa(Item item, Parcela parcela, String motivo) {
        if (mesmoValor(item.valor(), parcela.valor())) {
            return true;
        }
        BigDecimal diferenca = item.valor().subtract(parcela.valor()).abs();
        return motivo != null && !motivo.isBlank()
                && diferenca.compareTo(parcela.valor().multiply(VARIACAO_MAXIMA_IA)) <= 0;
    }

    private static String divergencia(Item item, Parcela parcela) {
        if (item.numero() == parcela.numero() && item.quantidade() == parcela.quantidade()) {
            return null;
        }
        return "Parcelamento diverge: " + item.numero() + "/" + item.quantidade() + " no PDF, "
                + parcela.numero() + "/" + parcela.quantidade() + " no sistema.";
    }

    private static String motivoValor(Parcela parcela) {
        String base = "Mesmo valor de \"" + parcela.descricao() + "\" (parcela " + parcela.numero() + "/"
                + parcela.quantidade() + ")";
        if (parcela.vizinha()) {
            YearMonth mes = YearMonth.of(parcela.competencia() / 100, parcela.competencia() % 100);
            return base + ", lançada na fatura de " + String.format("%02d/%d", mes.getMonthValue(), mes.getYear())
                    + ". As datas da saída divergem da fatura.";
        }
        return base + ", que ainda não tem a descrição da fatura.";
    }

    private static String motivoIa(Item item, Parcela parcela, String motivo) {
        String texto = "Indicada pela IA" + (motivo == null || motivo.isBlank() ? "" : ": " + motivo.strip());
        if (!mesmoValor(item.valor(), parcela.valor())) {
            texto += " (valor no sistema: " + parcela.valor().toPlainString() + ")";
        }
        if (parcela.vizinha()) {
            texto += " A parcela está em outro mês.";
        }
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }

    /** Mesmo cartão vale mais que qualquer similaridade de descrição (que vai de 0 a 1). */
    private static double pontuacao(Item item, Parcela parcela) {
        boolean mesmoCartao = item.cartaoId() != null && item.cartaoId().equals(parcela.cartaoId());
        return (mesmoCartao ? 10 : 0) + similaridade(item.descricao(), parcela.descricao(), parcela.descricaoFatura());
    }

    static boolean mesmoValor(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCIA) <= 0;
    }

    private static <T> T melhor(List<T> candidatos, Predicate<T> filtro, ToDoubleFunction<T> pontuacao) {
        return candidatos.stream()
                .filter(filtro)
                .max(Comparator.comparingDouble(pontuacao))
                .orElse(null);
    }

    /** Recua do vencimento da fatura até a parcela 1, para a saída nascer com as parcelas anteriores. */
    static LocalDate primeiroVencimento(LocalDate vencimentoFatura, int numeroParcela) {
        return vencimentoFatura.minusMonths(numeroParcela - 1L);
    }

    /** Fração dos termos da descrição do PDF que aparecem nas descrições da saída (0 a 1). */
    static double similaridade(String descricaoPdf, String... candidatas) {
        Set<String> termos = termos(descricaoPdf);
        if (termos.isEmpty()) {
            return 0;
        }
        Set<String> alvo = Arrays.stream(candidatas)
                .filter(Objects::nonNull)
                .flatMap(texto -> termos(texto).stream())
                .collect(Collectors.toSet());
        long comuns = termos.stream().filter(alvo::contains).count();
        return (double) comuns / termos.size();
    }

    private static Set<String> termos(String texto) {
        if (texto == null) {
            return Set.of();
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
        return Arrays.stream(normalizado.split(" "))
                .filter(termo -> termo.length() >= 3 && !termo.chars().allMatch(Character::isDigit))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Final do cartão lido pela IA: os 4 últimos dígitos, ou só 3 quando a fatura mascara um deles
     * (ex.: "*117"). O cartão é casado depois por sufixo.
     */
    static String normalizarDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.length() < 3 ? null : digitos.substring(Math.max(0, digitos.length() - 4));
    }

    static LocalDate converterData(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim().length() > 10 ? valor.trim().substring(0, 10) : valor.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
