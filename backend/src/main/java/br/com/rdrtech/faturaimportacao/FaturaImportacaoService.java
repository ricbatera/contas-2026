package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.exception.BusinessException;
import br.com.rdrtech.exception.FaturaImportacaoEmProcessamentoException;
import br.com.rdrtech.exception.FaturaImportacaoNaoEncontradaException;
import br.com.rdrtech.faturaimportacao.LeitorFaturaIa.OpcaoCategoria;
import br.com.rdrtech.faturaimportacao.LeitorFaturaIa.OpcaoSubcategoria;
import br.com.rdrtech.faturaimportacao.PdfFaturaLeitor.PdfLido;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoListaResponse;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoRequest;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoResponse;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoStatusResponse;
import br.com.rdrtech.faturaimportacao.dto.ResumoImportacao;
import br.com.rdrtech.ia.ProvedorIaService;
import br.com.rdrtech.presaida.PreSaidaMapper;
import br.com.rdrtech.presaida.PreSaidaRepository;
import br.com.rdrtech.subcategoria.Subcategoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
 * Upload e leitura de faturas em PDF. A chamada à IA pode levar minutos, então roda em segundo
 * plano ({@link ExecutorImportacaoFatura}) e fora de transação: a importação é gravada como
 * PROCESSANDO no upload e a conciliação acontece numa transação própria depois da resposta.
 * O frontend acompanha pelo endpoint de status até CONCLUIDA ou ERRO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaturaImportacaoService {

    private static final int TAMANHO_MAXIMO_ERRO = 2000;

    private final FaturaImportacaoRepository importacaoRepository;
    private final PreSaidaRepository preSaidaRepository;
    private final CategoriaRepository categoriaRepository;
    private final PdfFaturaLeitor pdfLeitor;
    private final LeitorFaturaIa leitorIa;
    private final ConciliacaoFaturaService conciliacaoService;
    private final ProvedorIaService provedorIaService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final ExecutorImportacaoFatura executor;

    public record ArquivoPdf(String nomeArquivo, byte[] conteudo) {
    }

    public List<FaturaImportacaoListaResponse> listar() {
        return importacaoRepository.listar();
    }

    public FaturaImportacaoResponse buscarPorId(UUID id) {
        FaturaImportacao importacao = buscarEntidade(id);
        var preSaidas = preSaidaRepository.listarPorImportacao(id).stream()
                .map(PreSaidaMapper::toResponse)
                .toList();
        return FaturaImportacaoMapper.toResponse(importacao, lerResumo(importacao.getResumo()), preSaidas);
    }

    public ArquivoPdf baixarPdf(UUID id) {
        FaturaImportacao importacao = buscarEntidade(id);
        return new ArquivoPdf(importacao.getNomeArquivo(), importacao.getPdf());
    }

    public FaturaImportacaoStatusResponse buscarStatus(UUID id) {
        return importacaoRepository.buscarStatus(id).orElseThrow(FaturaImportacaoNaoEncontradaException::new);
    }

    /**
     * Valida o arquivo e a senha na hora (erros voltam ao usuário imediatamente), grava a
     * importação como PROCESSANDO e agenda a leitura por IA em segundo plano.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FaturaImportacaoResponse importar(FaturaImportacaoRequest request) {
        // Falha antes de gravar qualquer coisa se não houver provedor de IA padrão.
        provedorIaService.chatModelPadrao();
        byte[] original = pdfLeitor.decodificar(request.conteudoBase64());
        PdfLido pdf = pdfLeitor.ler(original, request.senha());

        UUID id = emTransacao(() -> importacaoRepository.save(FaturaImportacao.builder()
                .nomeArquivo(request.nomeArquivo().trim())
                .pdf(pdf.pdfDesbloqueado())
                .hashSha256(sha256(original))
                .tamanhoBytes(original.length)
                .status(StatusImportacao.PROCESSANDO)
                .build()).getId());
        agendar(id, pdf.texto());
        return emTransacao(() -> buscarPorId(id));
    }

    /** Lê de novo o PDF gravado (já sem senha), por exemplo depois de cadastrar o cartão que faltava. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FaturaImportacaoResponse reprocessar(UUID id) {
        provedorIaService.chatModelPadrao();
        emTransacao(() -> {
            FaturaImportacao importacao = buscarEntidade(id);
            if (importacao.getStatus() == StatusImportacao.PROCESSANDO) {
                throw new FaturaImportacaoEmProcessamentoException();
            }
            importacao.setStatus(StatusImportacao.PROCESSANDO);
            importacao.setErro(null);
            return null;
        });
        agendar(id, null);
        return emTransacao(() -> buscarPorId(id));
    }

    @Transactional
    public void excluir(UUID id) {
        FaturaImportacao importacao = buscarEntidade(id);
        if (importacao.getStatus() == StatusImportacao.PROCESSANDO) {
            throw new FaturaImportacaoEmProcessamentoException();
        }
        importacaoRepository.delete(importacao);
    }

    private void agendar(UUID id, String texto) {
        if (!executor.agendar(() -> executarProcessamento(id, texto))) {
            registrarErro(id, "Há muitas faturas sendo processadas. Reprocesse esta importação em instantes.", null, null);
        }
    }

    /** Roda na thread do {@link ExecutorImportacaoFatura}; qualquer falha termina como status ERRO. */
    private void executarProcessamento(UUID id, String texto) {
        try {
            ChatModel chatModel = provedorIaService.chatModelPadrao();
            String conteudo = texto != null
                    ? texto
                    : pdfLeitor.ler(emTransacao(() -> buscarEntidade(id).getPdf()), null).texto();
            processar(id, chatModel, conteudo);
        } catch (BusinessException e) {
            registrarErro(id, e.getMessage(), null, null);
        } catch (RuntimeException e) {
            log.error("Erro ao processar a importação de fatura {}", id, e);
            registrarErro(id, "Erro inesperado ao processar a fatura.", null, null);
        }
    }

    private void processar(UUID id, ChatModel chatModel, String texto) {
        List<OpcaoCategoria> categorias = emTransacao(this::opcoesCategorias);
        LeitorFaturaIa.Resposta resposta;
        try {
            resposta = leitorIa.consultar(chatModel, texto, categorias);
        } catch (RuntimeException e) {
            log.warn("Falha ao consultar a IA na importação de fatura {}: {}", id, e.toString());
            registrarErro(id, "Falha ao consultar o provedor de IA: " + ProvedorIaService.mensagemErro(e), null, null);
            return;
        }

        try {
            FaturaLidaIa lida = leitorIa.interpretar(resposta.texto());
            List<String> avisosDesempate = new ArrayList<>();
            List<MotorConciliacao.ParIa> pares = desempatar(id, chatModel, lida, avisosDesempate);
            emTransacao(() -> {
                FaturaImportacao importacao = buscarEntidade(id);
                importacao.setRespostaIa(resposta.texto());
                importacao.setProvedorModelo(limitar(resposta.modelo(), 100));
                preSaidaRepository.excluirPendentesDaImportacao(id);
                ResumoImportacao resumo = conciliacaoService.aplicar(importacao, lida, pares, avisosDesempate);
                if (importacaoRepository.existeOutraComHash(importacao.getHashSha256(), id)) {
                    List<String> avisos = new ArrayList<>();
                    avisos.add("Este mesmo arquivo já foi importado antes; itens repetidos não foram duplicados.");
                    avisos.addAll(resumo.avisos());
                    resumo = new ResumoImportacao(resumo.conciliados(), resumo.ignorados(), avisos,
                            resumo.preSaidasCriadas(), resumo.totalSistema(), resumo.totalPreSaidas());
                }
                importacao.setResumo(escreverResumo(resumo));
                importacao.setStatus(StatusImportacao.CONCLUIDA);
                importacao.setErro(null);
                return null;
            });
        } catch (BusinessException e) {
            registrarErro(id, e.getMessage(), resposta.texto(), resposta.modelo());
        } catch (RuntimeException e) {
            log.error("Erro ao conciliar a importação de fatura {}", id, e);
            registrarErro(id, "Erro inesperado ao conciliar a fatura.", resposta.texto(), resposta.modelo());
        }
    }

    /**
     * Segunda consulta à IA, só quando sobram itens sem par e parcelas que podem ser deles. Uma
     * falha aqui não impede a importação: as sugestões passam a usar só valor e descrição.
     */
    private List<MotorConciliacao.ParIa> desempatar(UUID id, ChatModel chatModel, FaturaLidaIa lida,
            List<String> avisos) {
        MotorConciliacao.Desempate desempate = emTransacao(() -> conciliacaoService.planejar(id, lida));
        if (!desempate.necessario()) {
            return List.of();
        }
        try {
            return leitorIa.desempatar(chatModel, desempate);
        } catch (RuntimeException e) {
            log.warn("Falha no desempate por IA da importação de fatura {}: {}", id, e.toString());
            avisos.add("Não foi possível consultar a IA para comparar com as saídas já lançadas; "
                    + "as sugestões usam só valor e descrição.");
            return List.of();
        }
    }

    private void registrarErro(UUID id, String mensagem, String respostaIa, String modelo) {
        emTransacao(() -> {
            // A importação pode ter sido excluída enquanto processava.
            FaturaImportacao importacao = importacaoRepository.buscarPorId(id).orElse(null);
            if (importacao == null) {
                return null;
            }
            importacao.setStatus(StatusImportacao.ERRO);
            importacao.setErro(limitar(mensagem, TAMANHO_MAXIMO_ERRO));
            if (respostaIa != null) {
                importacao.setRespostaIa(respostaIa);
            }
            if (modelo != null) {
                importacao.setProvedorModelo(limitar(modelo, 100));
            }
            return null;
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

    private FaturaImportacao buscarEntidade(UUID id) {
        return importacaoRepository.buscarPorId(id).orElseThrow(FaturaImportacaoNaoEncontradaException::new);
    }

    private <T> T emTransacao(Supplier<T> acao) {
        return new TransactionTemplate(transactionManager).execute(status -> acao.get());
    }

    private ResumoImportacao lerResumo(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ResumoImportacao.class);
        } catch (JsonProcessingException e) {
            log.warn("Resumo de importação ilegível: {}", e.getOriginalMessage());
            return null;
        }
    }

    private String escreverResumo(ResumoImportacao resumo) {
        try {
            return objectMapper.writeValueAsString(resumo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível gravar o resumo da importação", e);
        }
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
