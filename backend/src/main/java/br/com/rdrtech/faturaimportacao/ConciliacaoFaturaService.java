package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.cartao.CartaoRepository;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.exception.FaturaImportacaoInvalidaException;
import br.com.rdrtech.fatura.Fatura;
import br.com.rdrtech.fatura.FaturaRepository;
import br.com.rdrtech.faturaimportacao.FaturaLidaIa.ItemLido;
import br.com.rdrtech.faturaimportacao.dto.ResumoImportacao;
import br.com.rdrtech.faturaimportacao.dto.ResumoImportacao.ItemConciliado;
import br.com.rdrtech.faturaimportacao.dto.ResumoImportacao.ItemIgnorado;
import br.com.rdrtech.presaida.PreSaida;
import br.com.rdrtech.presaida.PreSaidaRepository;
import br.com.rdrtech.presaida.StatusPreSaida;
import br.com.rdrtech.saida.Saida;
import br.com.rdrtech.saida.SaidaParcela;
import br.com.rdrtech.subcategoria.Subcategoria;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confronta os itens lidos da fatura com as parcelas já lançadas. Item que casa com uma parcela
 * só atualiza a descrição de fatura da saída; item sem correspondência vira {@link PreSaida}.
 * A decisão é determinística (valor, número e quantidade de parcelas); a IA só extrai os dados.
 */
@Service
@RequiredArgsConstructor
public class ConciliacaoFaturaService {

    static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final int TAMANHO_DESCRICAO = 120;
    private static final int LIMITE_AVISOS_FALTANTES = 20;
    private static final Set<String> TIPOS_QUE_GERAM_SAIDA = Set.of("COMPRA", "TARIFA", "IOF", "JUROS");

    private final CartaoRepository cartaoRepository;
    private final FaturaRepository faturaRepository;
    private final PreSaidaRepository preSaidaRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional
    public ResumoImportacao conciliar(FaturaImportacao importacao, FaturaLidaIa lida) {
        LocalDate vencimento = converterData(lida.vencimento());
        if (vencimento == null) {
            throw new FaturaImportacaoInvalidaException("A IA não identificou a data de vencimento da fatura.");
        }
        int competencia = vencimento.getYear() * 100 + vencimento.getMonthValue();
        List<String> avisos = new ArrayList<>();
        CartoesIdentificados cartoes = identificarCartoes(lida, avisos);

        importacao.setCartaoPrincipal(cartoes.principal());
        importacao.setCompetencia(competencia);
        importacao.setVencimento(vencimento);
        importacao.setTotalPdf(lida.total());

        Fatura fatura = faturaRepository.buscarPorCartaoECompetencia(cartoes.principal().getId(), competencia)
                .orElse(null);
        List<SaidaParcela> parcelasLivres = fatura == null ? new ArrayList<>() : new ArrayList<>(fatura.getParcelas());
        BigDecimal totalSistema = fatura == null ? BigDecimal.ZERO : fatura.getTotal();
        List<PreSaida> preSaidasLivres = new ArrayList<>(
                preSaidaRepository.listarNaoAprovadasDaFatura(cartoes.principal().getId(), competencia));
        SugestoesCategoria sugestoes = carregarSugestoes();

        List<ItemConciliado> conciliados = new ArrayList<>();
        List<ItemIgnorado> ignorados = new ArrayList<>();
        int criadas = 0;
        BigDecimal totalPreSaidas = BigDecimal.ZERO;

        for (ItemLido item : lida.itens()) {
            String descricao = descricaoDoItem(item);
            String tipo = item.tipo() == null ? "COMPRA" : item.tipo().trim().toUpperCase(Locale.ROOT);
            if (item.valor() == null || item.valor().signum() <= 0 || !TIPOS_QUE_GERAM_SAIDA.contains(tipo)) {
                ignorados.add(new ItemIgnorado(descricao, item.valor(), tipo, motivoIgnorado(tipo, item.valor())));
                continue;
            }
            BigDecimal valor = item.valor().setScale(2, RoundingMode.HALF_UP);
            int numero = item.parcelaAtual() == null ? 1 : item.parcelaAtual();
            int quantidade = item.totalParcelas() == null ? 1 : item.totalParcelas();
            if (quantidade < 1 || quantidade > 60 || numero < 1 || numero > quantidade) {
                ignorados.add(new ItemIgnorado(descricao, valor, tipo,
                        "Parcelamento inválido (" + numero + "/" + quantidade + ")"));
                continue;
            }
            String digitos = normalizarDigitos(item.ultimosDigitos());
            Cartao cartao = digitos == null ? cartoes.unicoOuPrincipal() : cartoes.porDigitos().get(digitos);
            if (digitos != null && cartao == null) {
                avisos.add("Cartão final " + digitos + " não cadastrado: \"" + descricao
                        + "\" ficou sem cartão na pré-saída.");
            }

            SaidaParcela parcela = melhor(parcelasLivres,
                    p -> mesmoValor(p.getValor(), valor)
                            && p.getNumero() == numero
                            && p.getSaida().getQuantidadeParcelas() == quantidade,
                    p -> (cartao != null && p.getSaida().getCartao() != null
                            && cartao.getId().equals(p.getSaida().getCartao().getId()) ? 10 : 0)
                            + similaridade(descricao, p.getSaida().getDescricao(), p.getSaida().getDescricaoFatura()));
            if (parcela != null) {
                parcelasLivres.remove(parcela);
                Saida saida = parcela.getSaida();
                String anterior = saida.getDescricaoFatura();
                saida.setDescricaoFatura(descricao);
                conciliados.add(new ItemConciliado(descricao, digitos, valor, numero, quantidade,
                        saida.getId(), saida.getDescricao(), anterior));
                continue;
            }

            PreSaida existente = melhor(preSaidasLivres,
                    p -> mesmoValor(p.getValorPorParcela(), valor)
                            && p.getNumeroParcela() == numero
                            && p.getQuantidadeParcelas() == quantidade,
                    p -> similaridade(descricao, p.getDescricaoFatura(), p.getDescricao()));
            if (existente != null) {
                preSaidasLivres.remove(existente);
                if (existente.getStatus() == StatusPreSaida.REJEITADA) {
                    ignorados.add(new ItemIgnorado(descricao, valor, tipo, "Pré-saída rejeitada anteriormente"));
                } else {
                    existente.setImportacao(importacao);
                    totalPreSaidas = totalPreSaidas.add(valor);
                }
                continue;
            }

            parcelasLivres.stream()
                    .filter(p -> mesmoValor(p.getValor(), valor) && p.getSaida().getQuantidadeParcelas() == quantidade)
                    .findFirst()
                    .ifPresent(p -> avisos.add("\"" + descricao + "\" (" + numero + "/" + quantidade
                            + ") parece com a saída \"" + p.getSaida().getDescricao() + "\", lançada como parcela "
                            + p.getNumero() + "/" + quantidade + " nesta fatura. Confira antes de aprovar."));

            Sugestao sugestao = sugestoes.resolver(item.categoriaId(), item.subcategoriaId());
            preSaidaRepository.save(PreSaida.builder()
                    .importacao(importacao)
                    .cartao(cartao)
                    .descricao(descricao)
                    .descricaoFatura(descricao)
                    .dataCompra(converterData(item.data()))
                    .valorPorParcela(valor)
                    .numeroParcela(numero)
                    .quantidadeParcelas(quantidade)
                    .competencia(competencia)
                    .primeiroVencimento(primeiroVencimento(vencimento, numero))
                    .categoria(sugestao.categoria())
                    .subcategoria(sugestao.subcategoria())
                    .status(StatusPreSaida.PENDENTE)
                    .build());
            criadas++;
            totalPreSaidas = totalPreSaidas.add(valor);
        }

        parcelasLivres.stream()
                .sorted(Comparator.comparing(p -> p.getSaida().getDescricao()))
                .limit(LIMITE_AVISOS_FALTANTES)
                .forEach(p -> avisos.add("A parcela " + p.getNumero() + "/" + p.getSaida().getQuantidadeParcelas()
                        + " de \"" + p.getSaida().getDescricao() + "\" está no sistema mas não foi encontrada no PDF."));
        if (parcelasLivres.size() > LIMITE_AVISOS_FALTANTES) {
            avisos.add("E mais " + (parcelasLivres.size() - LIMITE_AVISOS_FALTANTES)
                    + " parcelas do sistema não encontradas no PDF.");
        }
        return new ResumoImportacao(conciliados, ignorados, avisos, criadas, totalSistema, totalPreSaidas);
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

    private static <T> T melhor(List<T> candidatos, Predicate<T> filtro, ToDoubleFunction<T> pontuacao) {
        return candidatos.stream()
                .filter(filtro)
                .max(Comparator.comparingDouble(pontuacao))
                .orElse(null);
    }

    private static boolean mesmoValor(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCIA) <= 0;
    }

    private CartoesIdentificados identificarCartoes(FaturaLidaIa lida, List<String> avisos) {
        Set<String> digitos = new LinkedHashSet<>();
        lida.cartoes().forEach(c -> adicionarSeValido(digitos, normalizarDigitos(c.ultimosDigitos())));
        lida.itens().forEach(i -> adicionarSeValido(digitos, normalizarDigitos(i.ultimosDigitos())));
        if (digitos.isEmpty()) {
            throw new FaturaImportacaoInvalidaException(
                    "A IA não identificou os 4 últimos dígitos de nenhum cartão na fatura.");
        }

        List<Cartao> encontrados = cartaoRepository.listarPorUltimosDigitos(digitos);
        Map<UUID, Set<String>> digitosPorPrincipal = new HashMap<>();
        Map<UUID, Cartao> principais = new HashMap<>();
        for (Cartao cartao : encontrados) {
            Cartao principal = principalDe(cartao);
            principais.put(principal.getId(), principal);
            digitosPorPrincipal.computeIfAbsent(principal.getId(), id -> new LinkedHashSet<>())
                    .add(cartao.getUltimosDigitos());
        }
        if (principais.isEmpty()) {
            throw new FaturaImportacaoInvalidaException("Nenhum cartão cadastrado termina em "
                    + String.join(", ", digitos) + ". Cadastre o cartão e reprocesse a importação.");
        }
        int maiorCobertura = digitosPorPrincipal.values().stream().mapToInt(Set::size).max().orElse(0);
        List<UUID> candidatos = digitosPorPrincipal.entrySet().stream()
                .filter(entry -> entry.getValue().size() == maiorCobertura)
                .map(Map.Entry::getKey)
                .toList();
        if (candidatos.size() > 1) {
            throw new FaturaImportacaoInvalidaException("Os finais " + String.join(", ", digitos)
                    + " correspondem a mais de um cartão principal; não foi possível identificar a fatura.");
        }
        Cartao principal = principais.get(candidatos.getFirst());

        Map<String, Cartao> porDigitos = new LinkedHashMap<>();
        encontrados.stream()
                .filter(cartao -> principalDe(cartao).getId().equals(principal.getId()))
                .sorted(Comparator.comparing(Cartao::isAtivo).reversed())
                .forEach(cartao -> porDigitos.putIfAbsent(cartao.getUltimosDigitos(), cartao));
        digitos.stream()
                .filter(d -> !porDigitos.containsKey(d))
                .forEach(d -> avisos.add("O final " + d + " aparece na fatura, mas não é um cartão de "
                        + principal.getNome() + "."));
        return new CartoesIdentificados(principal, porDigitos);
    }

    private SugestoesCategoria carregarSugestoes() {
        Map<UUID, Categoria> categorias = new HashMap<>();
        Map<UUID, Subcategoria> subcategorias = new HashMap<>();
        for (Categoria categoria : categoriaRepository.listarComSubcategorias()) {
            if (categoria.getTipo() != TipoCategoria.DESPESA || !categoria.isAtivo()) {
                continue;
            }
            categorias.put(categoria.getId(), categoria);
            categoria.getSubcategorias().stream()
                    .filter(Subcategoria::isAtivo)
                    .forEach(sub -> subcategorias.put(sub.getId(), sub));
        }
        return new SugestoesCategoria(categorias, subcategorias);
    }

    private static Cartao principalDe(Cartao cartao) {
        return cartao.getCartaoPrincipal() == null ? cartao : cartao.getCartaoPrincipal();
    }

    private static void adicionarSeValido(Set<String> digitos, String valor) {
        if (valor != null) {
            digitos.add(valor);
        }
    }

    static String normalizarDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.length() < 4 ? null : digitos.substring(digitos.length() - 4);
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

    private static String descricaoDoItem(ItemLido item) {
        String descricao = item.descricao() == null ? "" : item.descricao().strip().replaceAll("\\s+", " ");
        if (descricao.isEmpty()) {
            descricao = "Lançamento sem descrição";
        }
        return descricao.length() > TAMANHO_DESCRICAO ? descricao.substring(0, TAMANHO_DESCRICAO) : descricao;
    }

    private static String motivoIgnorado(String tipo, BigDecimal valor) {
        if ("PAGAMENTO".equals(tipo)) {
            return "Pagamento de fatura";
        }
        if ("ESTORNO".equals(tipo)) {
            return "Estorno ou crédito";
        }
        if (valor == null || valor.signum() <= 0) {
            return "Valor ausente, zerado ou negativo";
        }
        return "Tipo de lançamento não reconhecido";
    }

    private record CartoesIdentificados(Cartao principal, Map<String, Cartao> porDigitos) {

        /** Item sem dígitos: se a fatura só tem um cartão, é dele; senão fica com o principal. */
        Cartao unicoOuPrincipal() {
            return porDigitos.size() == 1 ? porDigitos.values().iterator().next() : principal;
        }
    }

    private record Sugestao(Categoria categoria, Subcategoria subcategoria) {
    }

    private record SugestoesCategoria(Map<UUID, Categoria> categorias, Map<UUID, Subcategoria> subcategorias) {

        /** Aceita só ids de categorias e subcategorias de despesa ativas; a subcategoria define a categoria. */
        Sugestao resolver(String categoriaId, String subcategoriaId) {
            Subcategoria subcategoria = buscar(subcategorias, subcategoriaId);
            if (subcategoria != null && categorias.containsKey(subcategoria.getCategoria().getId())) {
                return new Sugestao(categorias.get(subcategoria.getCategoria().getId()), subcategoria);
            }
            return new Sugestao(buscar(categorias, categoriaId), null);
        }

        private static <T> T buscar(Map<UUID, T> mapa, String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            try {
                return mapa.get(UUID.fromString(id.trim()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
