package br.com.rdrtech.comprovante;

import br.com.rdrtech.cartao.Bandeira;
import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.cartao.CartaoRepository;
import br.com.rdrtech.cartao.IdentificacaoCartao;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.comprovante.dto.ComprovanteEdicaoRequest;
import br.com.rdrtech.comprovante.dto.ComprovanteListaResponse;
import br.com.rdrtech.comprovante.dto.ComprovanteRequest;
import br.com.rdrtech.comprovante.dto.ComprovanteResponse;
import br.com.rdrtech.exception.BusinessException;
import br.com.rdrtech.exception.CategoriaNaoEncontradaException;
import br.com.rdrtech.exception.ComprovanteInvalidoException;
import br.com.rdrtech.exception.ComprovanteNaoEncontradoException;
import br.com.rdrtech.exception.ComprovanteStatusInvalidoException;
import br.com.rdrtech.exception.SubcategoriaNaoEncontradaException;
import br.com.rdrtech.fatura.FaturaRepository;
import br.com.rdrtech.faturaimportacao.LeitorFaturaIa.OpcaoCategoria;
import br.com.rdrtech.faturaimportacao.LeitorFaturaIa.OpcaoSubcategoria;
import br.com.rdrtech.ia.ProvedorIaService;
import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import br.com.rdrtech.saida.SaidaRepository;
import br.com.rdrtech.saida.SaidaService;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import dev.langchain4j.model.chat.ChatModel;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Comprovantes de maquininha lidos por IA. É uma imagem e uma consulta, então a leitura é
 * síncrona, mas roda fora de transação (como na importação de fatura) para não segurar conexão
 * enquanto o provedor responde. Uma falha da IA não impede o envio: o comprovante é gravado com
 * {@code erroLeitura} para o usuário preencher à mão ou reler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComprovanteService {

    static final int TAMANHO_MAXIMO_BYTES = 8 * 1024 * 1024;
    private static final int TAMANHO_MAXIMO_ERRO = 2000;

    private final ComprovanteCartaoRepository comprovanteRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final FaturaRepository faturaRepository;
    private final SaidaRepository saidaRepository;
    private final SaidaService saidaService;
    private final ProvedorIaService provedorIaService;
    private final LeitorComprovanteIa leitorIa;
    private final PlatformTransactionManager transactionManager;

    public record Imagem(byte[] conteudo, String tipoMidia) {
    }

    /** Resultado da consulta à IA: a leitura ou a mensagem de erro. */
    private record Consulta(LeitorComprovanteIa.Leitura leitura, String erro) {
    }

    public List<ComprovanteListaResponse> listar(StatusComprovante status) {
        return comprovanteRepository.listar().stream()
                .filter(item -> status == null || item.status() == status)
                .toList();
    }

    public ComprovanteResponse buscarPorId(UUID id) {
        return ComprovanteMapper.toResponse(buscarEntidade(id));
    }

    public Imagem baixarImagem(UUID id) {
        ComprovanteCartao comprovante = buscarEntidade(id);
        return new Imagem(comprovante.getImagem(), comprovante.getTipoMidia());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ComprovanteResponse enviar(ComprovanteRequest request) {
        // Falha antes de gravar qualquer coisa se não houver provedor de IA padrão.
        ChatModel chatModel = provedorIaService.chatModelPadrao();
        Imagem imagem = decodificar(request.conteudoBase64());
        Consulta consulta = consultar(chatModel, imagem);

        return emTransacao(() -> {
            ComprovanteCartao comprovante = ComprovanteCartao.builder()
                    .nomeArquivo(request.nomeArquivo().trim())
                    .imagem(imagem.conteudo())
                    .tipoMidia(imagem.tipoMidia())
                    .tamanhoBytes(imagem.conteudo().length)
                    .hashSha256(sha256(imagem.conteudo()))
                    .build();
            comprovanteRepository.save(comprovante);
            aplicarConsulta(comprovante, consulta);
            return ComprovanteMapper.toResponse(comprovante);
        });
    }

    /** Lê a imagem de novo e sobrescreve os campos com a nova leitura. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ComprovanteResponse reler(UUID id) {
        ChatModel chatModel = provedorIaService.chatModelPadrao();
        Imagem imagem = emTransacao(() -> {
            ComprovanteCartao comprovante = buscarPendente(id);
            return new Imagem(comprovante.getImagem(), comprovante.getTipoMidia());
        });
        Consulta consulta = consultar(chatModel, imagem);
        return emTransacao(() -> {
            ComprovanteCartao comprovante = buscarPendente(id);
            aplicarConsulta(comprovante, consulta);
            return ComprovanteMapper.toResponse(comprovante);
        });
    }

    @Transactional
    public ComprovanteResponse atualizar(UUID id, ComprovanteEdicaoRequest request) {
        ComprovanteCartao comprovante = buscarPendente(id);
        Cartao cartao = null;
        if (request.cartaoId() != null) {
            cartao = cartaoRepository.buscarPorId(request.cartaoId())
                    .orElseThrow(() -> new ComprovanteInvalidoException("Selecione um cartão cadastrado."));
        }
        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = categoriaRepository.buscarPorId(request.categoriaId())
                    .orElseThrow(CategoriaNaoEncontradaException::new);
        }
        Subcategoria subcategoria = null;
        if (request.subcategoriaId() != null) {
            UUID categoriaId = categoria == null ? null : categoria.getId();
            subcategoria = subcategoriaRepository.buscarPorId(request.subcategoriaId())
                    .filter(item -> item.getCategoria().getId().equals(categoriaId))
                    .orElseThrow(SubcategoriaNaoEncontradaException::new);
        }
        boolean debito = request.modalidade() == ModalidadeComprovante.DEBITO;

        comprovante.setModalidade(request.modalidade());
        comprovante.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? null : request.descricao().trim());
        comprovante.setCartao(cartao);
        comprovante.setCategoria(categoria);
        comprovante.setSubcategoria(subcategoria);
        comprovante.setDataCompra(request.dataCompra());
        comprovante.setValorTotal(request.valorTotal());
        comprovante.setQuantidadeParcelas(debito ? 1 : request.quantidadeParcelas());
        if (debito) {
            comprovante.setPrimeiroVencimento(null);
        } else if (request.primeiroVencimento() != null) {
            comprovante.setPrimeiroVencimento(request.primeiroVencimento());
        } else {
            comprovante.setPrimeiroVencimento(sugerirVencimento(cartao, request.dataCompra(), new ArrayList<>()));
        }
        return ComprovanteMapper.toResponse(comprovante);
    }

    /**
     * Cria a saída com o usuário como responsável. No crédito, as parcelas entram nas faturas a
     * partir do vencimento informado; no débito, é uma saída única na data da compra.
     */
    @Transactional
    public ComprovanteResponse aprovar(UUID id) {
        ComprovanteCartao comprovante = buscarPendente(id);
        boolean credito = comprovante.getModalidade() == ModalidadeComprovante.CREDITO;
        if (comprovante.getDescricao() == null || comprovante.getValorTotal() == null
                || comprovante.getDataCompra() == null
                || comprovante.getCategoria() == null || comprovante.getSubcategoria() == null) {
            throw new ComprovanteInvalidoException(
                    "Informe descrição, data, valor, categoria e subcategoria antes de aprovar.");
        }
        if (credito && (comprovante.getCartao() == null || comprovante.getPrimeiroVencimento() == null)) {
            throw new ComprovanteInvalidoException(
                    "Informe o cartão e o vencimento da primeira parcela antes de aprovar.");
        }
        int parcelas = credito ? comprovante.getQuantidadeParcelas() : 1;
        var saida = saidaService.criar(new SaidaRequest(
                comprovante.getDescricao(),
                null,
                ComprovanteMapper.valorPorParcela(comprovante.getValorTotal(), parcelas),
                parcelas,
                credito ? FormaPagamento.CARTAO_CREDITO : FormaPagamento.DEBITO,
                credito ? null : comprovante.getDataCompra(),
                credito ? comprovante.getPrimeiroVencimento() : null,
                credito ? comprovante.getCartao().getId() : null,
                comprovante.getCategoria().getId(),
                comprovante.getSubcategoria().getId(),
                ModoResponsabilidade.USUARIO,
                null,
                List.of()));
        comprovante.setSaida(saidaRepository.getReferenceById(saida.id()));
        comprovante.setStatus(StatusComprovante.APROVADO);
        return ComprovanteMapper.toResponse(comprovante);
    }

    @Transactional
    public ComprovanteResponse rejeitar(UUID id) {
        ComprovanteCartao comprovante = buscarPendente(id);
        comprovante.setStatus(StatusComprovante.REJEITADO);
        return ComprovanteMapper.toResponse(comprovante);
    }

    @Transactional
    public ComprovanteResponse reabrir(UUID id) {
        ComprovanteCartao comprovante = buscarEntidade(id);
        if (comprovante.getStatus() != StatusComprovante.REJEITADO) {
            throw new ComprovanteStatusInvalidoException("Só comprovantes rejeitados podem ser reabertos.");
        }
        comprovante.setStatus(StatusComprovante.PENDENTE);
        return ComprovanteMapper.toResponse(comprovante);
    }

    /** O aprovado fica como registro da saída criada; para desfazer, exclua a saída. */
    @Transactional
    public void excluir(UUID id) {
        ComprovanteCartao comprovante = buscarEntidade(id);
        if (comprovante.getStatus() == StatusComprovante.APROVADO) {
            throw new ComprovanteStatusInvalidoException("Comprovantes aprovados não podem ser excluídos.");
        }
        comprovanteRepository.delete(comprovante);
    }

    /** Chamada à IA fora de transação. Falhas viram mensagem de erro, não exceção. */
    private Consulta consultar(ChatModel chatModel, Imagem imagem) {
        List<OpcaoCategoria> categorias = emTransacao(this::opcoesCategorias);
        try {
            return new Consulta(leitorIa.ler(chatModel, imagem.conteudo(), imagem.tipoMidia(), categorias,
                    LocalDate.now()), null);
        } catch (BusinessException e) {
            return new Consulta(null, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Falha ao consultar a IA para ler um comprovante: {}", e.toString());
            return new Consulta(null, "Falha ao consultar o provedor de IA: " + ProvedorIaService.mensagemErro(e));
        }
    }

    /** Preenche o comprovante com a leitura: cartão pelo final, vencimento pelo cartão, categoria sugerida. */
    private void aplicarConsulta(ComprovanteCartao comprovante, Consulta consulta) {
        List<String> avisos = new ArrayList<>();
        if (comprovanteRepository.existeOutroComHash(comprovante.getHashSha256(), comprovante.getId())) {
            avisos.add("Esta mesma imagem já foi enviada antes; confira se a compra não está duplicada.");
        }
        if (consulta.leitura() == null) {
            comprovante.setErroLeitura(limitar(consulta.erro(), TAMANHO_MAXIMO_ERRO));
            comprovante.setAvisos(String.join("\n", avisos));
            return;
        }

        ComprovanteLidoIa lido = consulta.leitura().comprovante();
        comprovante.setErroLeitura(Boolean.FALSE.equals(lido.legivel())
                ? "A IA não conseguiu ler o comprovante na imagem. Confira os dados ou envie outra foto."
                : null);
        comprovante.setRespostaIa(consulta.leitura().respostaBruta());
        comprovante.setProvedorModelo(limitar(consulta.leitura().modelo(), 100));

        String estabelecimento = limitar(textoOuNulo(lido.estabelecimento()), 120);
        comprovante.setEstabelecimento(estabelecimento);
        comprovante.setDescricao(estabelecimento);
        String digitos = IdentificacaoCartao.normalizarDigitos(lido.ultimosDigitos());
        comprovante.setUltimosDigitosLidos(digitos);
        Bandeira bandeira = converterBandeira(lido.bandeira());
        comprovante.setBandeiraLida(bandeira != null ? bandeira.name() : limitar(textoOuNulo(lido.bandeira()), 20));

        boolean debito = "DEBITO".equalsIgnoreCase(textoOuNulo(lido.modalidade()));
        comprovante.setModalidade(debito ? ModalidadeComprovante.DEBITO : ModalidadeComprovante.CREDITO);
        comprovante.setDataCompra(converterData(lido.data()));
        comprovante.setValorTotal(lido.valorTotal() != null && lido.valorTotal().signum() > 0
                ? lido.valorTotal().setScale(2, RoundingMode.HALF_UP)
                : null);
        int parcelas = lido.parcelas() == null ? 1 : Math.clamp(lido.parcelas(), 1, 60);
        comprovante.setQuantidadeParcelas(debito ? 1 : parcelas);

        Cartao cartao = IdentificacaoCartao.escolher(cartaoRepository.listarComPrincipal(), digitos, bandeira)
                .orElse(null);
        comprovante.setCartao(cartao);
        if (cartao == null) {
            avisos.add(digitos == null
                    ? "O comprovante não mostra o final do cartão; selecione o cartão."
                    : "Nenhum cartão cadastrado foi identificado pelo final " + digitos + "; selecione o cartão.");
        }
        comprovante.setPrimeiroVencimento(debito
                ? null
                : sugerirVencimento(cartao, comprovante.getDataCompra(), avisos));
        aplicarCategoria(comprovante, lido);
        comprovante.setAvisos(String.join("\n", avisos));
    }

    /**
     * Vencimento da fatura em que a compra entra. Com os dias cadastrados no cartão principal, é
     * exato; sem eles, usa o dia de vencimento da última fatura, no mês seguinte à compra.
     */
    private LocalDate sugerirVencimento(Cartao cartao, LocalDate dataCompra, List<String> avisos) {
        if (cartao == null || dataCompra == null) {
            return null;
        }
        Cartao principal = IdentificacaoCartao.principalDe(cartao);
        if (principal.getDiaFechamento() != null && principal.getDiaVencimento() != null) {
            return IdentificacaoCartao.primeiroVencimento(
                    dataCompra, principal.getDiaFechamento(), principal.getDiaVencimento());
        }
        var ultimaFatura = faturaRepository.listarCompetenciasPorCartao(principal.getId()).stream().findFirst();
        if (ultimaFatura.isPresent() && ultimaFatura.get().vencimento() != null) {
            avisos.add("Confira o vencimento: cadastre os dias de fechamento e vencimento do cartão "
                    + principal.getNome() + " para ele ser calculado com precisão.");
            return IdentificacaoCartao.vencimentoNoMesSeguinte(dataCompra, ultimaFatura.get().vencimento().getDayOfMonth());
        }
        avisos.add("Informe o vencimento da primeira parcela, ou cadastre os dias de fechamento e vencimento do cartão "
                + principal.getNome() + ".");
        return null;
    }

    private void aplicarCategoria(ComprovanteCartao comprovante, ComprovanteLidoIa lido) {
        comprovante.setCategoria(null);
        comprovante.setSubcategoria(null);
        UUID subcategoriaId = converterUuid(lido.subcategoriaId());
        if (subcategoriaId == null) {
            return;
        }
        subcategoriaRepository.buscarPorId(subcategoriaId)
                .filter(Subcategoria::isAtivo)
                .filter(sub -> sub.getCategoria().isAtivo() && sub.getCategoria().getTipo() == TipoCategoria.DESPESA)
                .ifPresent(sub -> {
                    comprovante.setCategoria(sub.getCategoria());
                    comprovante.setSubcategoria(sub);
                });
    }

    private List<OpcaoCategoria> opcoesCategorias() {
        return categoriaRepository.listarComSubcategorias().stream()
                .filter(categoria -> categoria.getTipo() == TipoCategoria.DESPESA && categoria.isAtivo())
                .sorted(Comparator.comparing(categoria -> categoria.getNome().toLowerCase()))
                .map(categoria -> new OpcaoCategoria(categoria.getId(), categoria.getNome(),
                        categoria.getSubcategorias().stream()
                                .filter(Subcategoria::isAtivo)
                                .sorted(Comparator.comparing(sub -> sub.getNome().toLowerCase()))
                                .map(sub -> new OpcaoSubcategoria(sub.getId(), sub.getNome()))
                                .toList()))
                .toList();
    }

    /** Aceita JPEG, PNG e WEBP, identificados pelo conteúdo (não pela extensão). */
    static Imagem decodificar(String conteudoBase64) {
        String conteudo = conteudoBase64.strip();
        int separador = conteudo.indexOf(',');
        if (conteudo.startsWith("data:") && separador > 0) {
            conteudo = conteudo.substring(separador + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(conteudo);
        } catch (IllegalArgumentException e) {
            throw new ComprovanteInvalidoException("O conteúdo enviado não é um base64 válido.");
        }
        if (bytes.length == 0) {
            throw new ComprovanteInvalidoException("A imagem enviada está vazia.");
        }
        if (bytes.length > TAMANHO_MAXIMO_BYTES) {
            throw new ComprovanteInvalidoException("A imagem deve ter no máximo 8 MB.");
        }
        String tipo = tipoMidia(bytes);
        if (tipo == null) {
            throw new ComprovanteInvalidoException("Envie uma foto em JPEG, PNG ou WEBP.");
        }
        return new Imagem(bytes, tipo);
    }

    private static String tipoMidia(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89
                && new String(bytes, 1, 3, StandardCharsets.US_ASCII).equals("PNG")) {
            return "image/png";
        }
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return "image/webp";
        }
        return null;
    }

    private static Bandeira converterBandeira(String valor) {
        String texto = textoOuNulo(valor);
        if (texto == null) {
            return null;
        }
        String normalizado = texto.toUpperCase().replaceAll("[^A-Z]", "");
        if (normalizado.startsWith("MASTER") || normalizado.equals("MAESTRO")) {
            return Bandeira.MASTERCARD;
        }
        if (normalizado.startsWith("VISA") || normalizado.equals("ELECTRON")) {
            return Bandeira.VISA;
        }
        if (normalizado.startsWith("AMERICAN")) {
            return Bandeira.AMEX;
        }
        try {
            return Bandeira.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate converterData(String valor) {
        String texto = textoOuNulo(valor);
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto.length() > 10 ? texto.substring(0, 10) : texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static UUID converterUuid(String valor) {
        String texto = textoOuNulo(valor);
        if (texto == null) {
            return null;
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() || valor.equalsIgnoreCase("null") ? null : valor.trim();
    }

    private ComprovanteCartao buscarPendente(UUID id) {
        ComprovanteCartao comprovante = buscarEntidade(id);
        if (comprovante.getStatus() != StatusComprovante.PENDENTE) {
            throw new ComprovanteStatusInvalidoException("O comprovante já foi "
                    + (comprovante.getStatus() == StatusComprovante.APROVADO ? "aprovado" : "rejeitado") + ".");
        }
        return comprovante;
    }

    private ComprovanteCartao buscarEntidade(UUID id) {
        return comprovanteRepository.buscarPorId(id).orElseThrow(ComprovanteNaoEncontradoException::new);
    }

    private <T> T emTransacao(Supplier<T> acao) {
        return new TransactionTemplate(transactionManager).execute(status -> acao.get());
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String limitar(String texto, int tamanho) {
        if (texto == null) {
            return null;
        }
        return texto.length() > tamanho ? texto.substring(0, tamanho) : texto;
    }
}
