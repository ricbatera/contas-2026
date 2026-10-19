package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.cartao.CartaoRepository;
import br.com.rdrtech.cartao.IdentificacaoCartao;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.exception.FaturaImportacaoInvalidaException;
import br.com.rdrtech.fatura.Fatura;
import br.com.rdrtech.fatura.FaturaRepository;
import br.com.rdrtech.faturaimportacao.FaturaLidaIa.ItemLido;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Decisao;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Desempate;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Exata;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Item;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Nova;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.ParIa;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Parcela;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.PorValor;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.PreSaidaExistente;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Reaproveitada;
import br.com.rdrtech.faturaimportacao.MotorConciliacao.Resultado;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confronta os itens lidos da fatura com as parcelas já lançadas, usando as regras de
 * {@link MotorConciliacao}. Item que casa com uma parcela atualiza a descrição de fatura da saída;
 * o resto vira {@link PreSaida}, às vezes com uma saída sugerida para o usuário confirmar.
 *
 * <p>Roda em duas passadas: {@link #planejar} (só leitura) descobre o que a IA deve comparar, e
 * {@link #aplicar} grava o resultado já com as indicações da IA. A chamada à IA fica entre as duas,
 * fora de transação.
 */
@Service
@RequiredArgsConstructor
public class ConciliacaoFaturaService {

    private static final int TAMANHO_DESCRICAO = 120;
    private static final int LIMITE_AVISOS_FALTANTES = 20;
    private static final Set<String> TIPOS_QUE_GERAM_SAIDA = Set.of("COMPRA", "TARIFA", "IOF", "JUROS");

    private final CartaoRepository cartaoRepository;
    private final FaturaRepository faturaRepository;
    private final PreSaidaRepository preSaidaRepository;
    private final CategoriaRepository categoriaRepository;

    /** Itens sem par e parcelas candidatas, para a IA indicar possíveis correspondências. */
    @Transactional(readOnly = true)
    public Desempate planejar(UUID importacaoId, FaturaLidaIa lida) {
        Contexto contexto = carregar(importacaoId, lida, new ArrayList<>());
        return MotorConciliacao.conciliar(contexto.itens(), contexto.parcelas(), contexto.preSaidas(), null)
                .desempate();
    }

    @Transactional
    public ResumoImportacao aplicar(FaturaImportacao importacao, FaturaLidaIa lida, List<ParIa> paresIa,
            List<String> avisosAdicionais) {
        List<String> avisos = new ArrayList<>(avisosAdicionais);
        Contexto contexto = carregar(importacao.getId(), lida, avisos);
        importacao.setCartaoPrincipal(contexto.principal());
        importacao.setCompetencia(contexto.competencia());
        importacao.setVencimento(contexto.vencimento());
        importacao.setTotalPdf(lida.total());

        Resultado resultado = MotorConciliacao.conciliar(
                contexto.itens(), contexto.parcelas(), contexto.preSaidas(), paresIa);
        SugestoesCategoria sugestoes = carregarSugestoes();
        List<ItemConciliado> conciliados = new ArrayList<>();
        List<ItemIgnorado> ignorados = new ArrayList<>();
        int criadas = 0;
        BigDecimal totalPreSaidas = BigDecimal.ZERO;

        for (int indice = 0; indice < lida.itens().size(); indice++) {
            ItemIgnorado ignorado = contexto.ignorados().get(indice);
            if (ignorado != null) {
                ignorados.add(ignorado);
                continue;
            }
            Item item = contexto.itemPorIndice().get(indice);
            Decisao decisao = resultado.decisoes().get(indice);
            switch (decisao) {
                case Exata exata -> conciliados.add(vincular(item, contexto.parcela(exata.parcela()), "EXATO", null));
                case PorValor porValor -> conciliados.add(vincular(item, contexto.parcela(porValor.parcela()), "VALOR",
                        porValor.observacao()));
                case Reaproveitada reaproveitada -> {
                    PreSaida existente = contexto.preSaidaPorId().get(reaproveitada.preSaida().id());
                    switch (existente.getStatus()) {
                        case REJEITADA -> ignorados.add(new ItemIgnorado(item.descricao(), item.valor(),
                                contexto.tipo(indice), "Pré-saída rejeitada anteriormente"));
                        case VINCULADA -> conciliados.add(new ItemConciliado(item.descricao(), item.digitos(),
                                item.valor(), item.numero(), item.quantidade(), existente.getSaida().getId(),
                                existente.getSaida().getDescricao(), existente.getSaida().getDescricaoFatura(),
                                "VINCULO", "Vinculada por você em uma importação anterior."));
                        default -> {
                            existente.setImportacao(importacao);
                            totalPreSaidas = totalPreSaidas.add(item.valor());
                        }
                    }
                }
                case Nova nova -> {
                    ItemLido lido = lida.itens().get(indice);
                    var categoria = sugestoes.resolver(lido.categoriaId(), lido.subcategoriaId());
                    var sugestao = nova.sugestao();
                    preSaidaRepository.save(PreSaida.builder()
                            .importacao(importacao)
                            .cartao(contexto.cartaoPorIndice().get(indice))
                            .descricao(item.descricao())
                            .descricaoFatura(item.descricao())
                            .dataCompra(MotorConciliacao.converterData(lido.data()))
                            .valorPorParcela(item.valor())
                            .numeroParcela(item.numero())
                            .quantidadeParcelas(item.quantidade())
                            .competencia(contexto.competencia())
                            .primeiroVencimento(MotorConciliacao.primeiroVencimento(contexto.vencimento(), item.numero()))
                            .categoria(categoria.categoria())
                            .subcategoria(categoria.subcategoria())
                            .saidaSugerida(sugestao == null ? null : contexto.parcela(sugestao.parcela()).getSaida())
                            .motivoSugestao(sugestao == null ? null : sugestao.motivo())
                            .status(StatusPreSaida.PENDENTE)
                            .build());
                    criadas++;
                    totalPreSaidas = totalPreSaidas.add(item.valor());
                }
            }
        }

        resultado.faltantes().stream()
                .sorted(Comparator.comparing(Parcela::descricao))
                .limit(LIMITE_AVISOS_FALTANTES)
                .forEach(p -> avisos.add("A parcela " + p.numero() + "/" + p.quantidade() + " de \"" + p.descricao()
                        + "\" está no sistema mas não foi encontrada no PDF."));
        if (resultado.faltantes().size() > LIMITE_AVISOS_FALTANTES) {
            avisos.add("E mais " + (resultado.faltantes().size() - LIMITE_AVISOS_FALTANTES)
                    + " parcelas do sistema não encontradas no PDF.");
        }
        return new ResumoImportacao(conciliados, ignorados, avisos, criadas, contexto.totalSistema(), totalPreSaidas);
    }

    private static ItemConciliado vincular(Item item, SaidaParcela parcela, String criterio, String observacao) {
        Saida saida = parcela.getSaida();
        String anterior = saida.getDescricaoFatura();
        saida.setDescricaoFatura(item.descricao());
        return new ItemConciliado(item.descricao(), item.digitos(), item.valor(), item.numero(), item.quantidade(),
                saida.getId(), saida.getDescricao(), anterior, criterio, observacao);
    }

    /** Lê o que a conciliação precisa: cartões, itens válidos, parcelas da competência e vizinhas. */
    private Contexto carregar(UUID importacaoId, FaturaLidaIa lida, List<String> avisos) {
        LocalDate vencimento = MotorConciliacao.converterData(lida.vencimento());
        if (vencimento == null) {
            throw new FaturaImportacaoInvalidaException("A IA não identificou a data de vencimento da fatura.");
        }
        YearMonth mes = YearMonth.from(vencimento);
        int competencia = competencia(mes);
        CartoesIdentificados cartoes = identificarCartoes(lida, avisos);

        List<Item> itens = new ArrayList<>();
        Map<Integer, Item> itemPorIndice = new HashMap<>();
        Map<Integer, ItemIgnorado> ignorados = new HashMap<>();
        Map<Integer, Cartao> cartaoPorIndice = new HashMap<>();
        Map<Integer, String> tipos = new HashMap<>();
        for (int indice = 0; indice < lida.itens().size(); indice++) {
            ItemLido lido = lida.itens().get(indice);
            String descricao = descricaoDoItem(lido);
            String tipo = lido.tipo() == null ? "COMPRA" : lido.tipo().trim().toUpperCase(Locale.ROOT);
            tipos.put(indice, tipo);
            if (lido.valor() == null || lido.valor().signum() <= 0 || !TIPOS_QUE_GERAM_SAIDA.contains(tipo)) {
                ignorados.put(indice, new ItemIgnorado(descricao, lido.valor(), tipo, motivoIgnorado(tipo, lido.valor())));
                continue;
            }
            BigDecimal valor = lido.valor().setScale(2, RoundingMode.HALF_UP);
            int numero = lido.parcelaAtual() == null ? 1 : lido.parcelaAtual();
            int quantidade = lido.totalParcelas() == null ? 1 : lido.totalParcelas();
            if (quantidade < 1 || quantidade > 60 || numero < 1 || numero > quantidade) {
                ignorados.put(indice, new ItemIgnorado(descricao, valor, tipo,
                        "Parcelamento inválido (" + numero + "/" + quantidade + ")"));
                continue;
            }
            String digitos = MotorConciliacao.normalizarDigitos(lido.ultimosDigitos());
            Cartao cartao = digitos == null ? cartoes.unicoOuPrincipal() : cartoes.porDigitos().get(digitos);
            if (digitos != null && cartao == null) {
                avisos.add("Cartão final " + digitos + " não cadastrado: \"" + descricao
                        + "\" ficou sem cartão na pré-saída.");
            }
            cartaoPorIndice.put(indice, cartao);
            Item item = new Item(indice, descricao, digitos, cartao == null ? null : cartao.getId(), valor, numero,
                    quantidade);
            itens.add(item);
            itemPorIndice.put(indice, item);
        }

        UUID principalId = cartoes.principal().getId();
        Map<UUID, SaidaParcela> parcelaPorId = new LinkedHashMap<>();
        List<Parcela> parcelas = new ArrayList<>();
        Fatura fatura = faturaRepository.buscarPorCartaoECompetencia(principalId, competencia).orElse(null);
        BigDecimal totalSistema = fatura == null ? BigDecimal.ZERO : fatura.getTotal();
        adicionarParcelas(fatura, false, Set.of(), parcelas, parcelaPorId);
        // No mês vizinho só interessam saídas sem parcela nesta fatura (lançadas com o mês deslocado).
        Set<UUID> saidasDaCompetencia = new HashSet<>();
        parcelas.forEach(parcela -> saidasDaCompetencia.add(parcela.saidaId()));
        for (YearMonth vizinho : List.of(mes.minusMonths(1), mes.plusMonths(1))) {
            adicionarParcelas(faturaRepository.buscarPorCartaoECompetencia(principalId, competencia(vizinho))
                    .orElse(null), true, saidasDaCompetencia, parcelas, parcelaPorId);
        }

        Map<UUID, PreSaida> preSaidaPorId = new LinkedHashMap<>();
        List<PreSaidaExistente> preSaidas = new ArrayList<>();
        for (PreSaida preSaida : preSaidaRepository.listarReaproveitaveis(principalId, competencia, importacaoId)) {
            preSaidaPorId.put(preSaida.getId(), preSaida);
            preSaidas.add(new PreSaidaExistente(preSaida.getId(), preSaida.getStatus(),
                    preSaida.getSaida() == null ? null : preSaida.getSaida().getId(), preSaida.getDescricao(),
                    preSaida.getDescricaoFatura(), preSaida.getValorPorParcela(), preSaida.getNumeroParcela(),
                    preSaida.getQuantidadeParcelas(), preSaida.decididaPeloUsuario()));
        }

        return new Contexto(cartoes.principal(), vencimento, competencia, totalSistema, itens, itemPorIndice,
                ignorados, cartaoPorIndice, tipos, parcelas, parcelaPorId, preSaidas, preSaidaPorId);
    }

    private static void adicionarParcelas(Fatura fatura, boolean vizinha, Set<UUID> saidasIgnoradas,
            List<Parcela> parcelas, Map<UUID, SaidaParcela> parcelaPorId) {
        if (fatura == null) {
            return;
        }
        for (SaidaParcela parcela : fatura.getParcelas()) {
            Saida saida = parcela.getSaida();
            if (saidasIgnoradas.contains(saida.getId())) {
                continue;
            }
            Cartao cartao = saida.getCartao();
            parcelaPorId.put(parcela.getId(), parcela);
            parcelas.add(new Parcela(parcela.getId(), saida.getId(), saida.getDescricao(), saida.getDescricaoFatura(),
                    cartao == null ? null : cartao.getId(), cartao == null ? null : cartao.getUltimosDigitos(),
                    parcela.getValor(), parcela.getNumero(), saida.getQuantidadeParcelas(), fatura.getCompetencia(),
                    vizinha));
        }
    }

    private static int competencia(YearMonth mes) {
        return mes.getYear() * 100 + mes.getMonthValue();
    }

    private CartoesIdentificados identificarCartoes(FaturaLidaIa lida, List<String> avisos) {
        Set<String> digitos = new LinkedHashSet<>();
        lida.cartoes().forEach(c -> adicionarSeValido(digitos, MotorConciliacao.normalizarDigitos(c.ultimosDigitos())));
        lida.itens().forEach(i -> adicionarSeValido(digitos, MotorConciliacao.normalizarDigitos(i.ultimosDigitos())));
        if (digitos.isEmpty()) {
            throw new FaturaImportacaoInvalidaException(
                    "A IA não identificou o final de nenhum cartão na fatura.");
        }

        // Algumas faturas mascaram um dígito do final (ex.: "*117"); por isso o casamento é por sufixo.
        List<Cartao> cadastrados = cartaoRepository.listarComPrincipal();
        Map<UUID, Set<String>> digitosPorPrincipal = new HashMap<>();
        Map<UUID, Cartao> principais = new HashMap<>();
        for (String finalLido : digitos) {
            for (Cartao cartao : cadastrados) {
                if (IdentificacaoCartao.terminaEm(cartao, finalLido)) {
                    Cartao principal = IdentificacaoCartao.principalDe(cartao);
                    principais.put(principal.getId(), principal);
                    digitosPorPrincipal.computeIfAbsent(principal.getId(), id -> new LinkedHashSet<>()).add(finalLido);
                }
            }
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
        for (String finalLido : digitos) {
            cadastrados.stream()
                    .filter(cartao -> IdentificacaoCartao.terminaEm(cartao, finalLido))
                    .filter(cartao -> IdentificacaoCartao.principalDe(cartao).getId().equals(principal.getId()))
                    .sorted(Comparator.comparing(Cartao::isAtivo).reversed())
                    .findFirst()
                    .ifPresent(cartao -> porDigitos.put(finalLido, cartao));
        }
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

    private static void adicionarSeValido(Set<String> digitos, String valor) {
        if (valor != null) {
            digitos.add(valor);
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

    private record Contexto(
            Cartao principal,
            LocalDate vencimento,
            int competencia,
            BigDecimal totalSistema,
            List<Item> itens,
            Map<Integer, Item> itemPorIndice,
            Map<Integer, ItemIgnorado> ignorados,
            Map<Integer, Cartao> cartaoPorIndice,
            Map<Integer, String> tipos,
            List<Parcela> parcelas,
            Map<UUID, SaidaParcela> parcelaPorId,
            List<PreSaidaExistente> preSaidas,
            Map<UUID, PreSaida> preSaidaPorId) {

        SaidaParcela parcela(Parcela retrato) {
            return parcelaPorId.get(retrato.parcelaId());
        }

        String tipo(int indice) {
            return tipos.get(indice);
        }
    }

    private record CartoesIdentificados(Cartao principal, Map<String, Cartao> porDigitos) {

        /** Item sem dígitos: se a fatura só tem um cartão, é dele; senão fica com o principal. */
        Cartao unicoOuPrincipal() {
            return porDigitos.size() == 1 ? porDigitos.values().iterator().next() : principal;
        }
    }

    private record SugestaoCategoria(Categoria categoria, Subcategoria subcategoria) {
    }

    private record SugestoesCategoria(Map<UUID, Categoria> categorias, Map<UUID, Subcategoria> subcategorias) {

        /** Aceita só ids de categorias e subcategorias de despesa ativas; a subcategoria define a categoria. */
        SugestaoCategoria resolver(String categoriaId, String subcategoriaId) {
            Subcategoria subcategoria = buscar(subcategorias, subcategoriaId);
            if (subcategoria != null && categorias.containsKey(subcategoria.getCategoria().getId())) {
                return new SugestaoCategoria(categorias.get(subcategoria.getCategoria().getId()), subcategoria);
            }
            return new SugestaoCategoria(buscar(categorias, categoriaId), null);
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
