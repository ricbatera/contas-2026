package br.com.rdrtech.ia;

import br.com.rdrtech.exception.LimiteProvedoresIaExcedidoException;
import br.com.rdrtech.exception.ProvedorIaDuplicadoException;
import br.com.rdrtech.exception.ProvedorIaInvalidoException;
import br.com.rdrtech.exception.ProvedorIaNaoEncontradoException;
import br.com.rdrtech.ia.dto.ProvedorIaRequest;
import br.com.rdrtech.ia.dto.ProvedorIaResponse;
import br.com.rdrtech.ia.dto.TesteIaResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProvedorIaService {

    private static final int LIMITE_PROVEDORES = 20;
    private static final int TAMANHO_MAXIMO_ERRO = 1000;
    private static final Pattern MENSAGEM_JSON = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern CODIGO_JSON = Pattern.compile("\"code\"\\s*:\\s*(\\d{3})");
    static final String PROMPT_PADRAO = "Responda apenas com \"OK\" para confirmar que a integração está funcionando.";

    private final ProvedorIaRepository provedorIaRepository;
    private final CifradorSegredos cifradorSegredos;
    private final ChatModelFactory chatModelFactory;

    public List<ProvedorIaResponse> listar() {
        return provedorIaRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ProvedorIaResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidade(id));
    }

    @Transactional
    public ProvedorIaResponse criar(ProvedorIaRequest request) {
        if (provedorIaRepository.count() >= LIMITE_PROVEDORES) {
            throw new LimiteProvedoresIaExcedidoException();
        }
        validarDuplicidade(request.nome(), null);
        String apiKey = textoOuNull(request.apiKey());
        validarPorTipo(request, apiKey != null);
        if (request.padrao()) {
            desmarcarPadraoAtual();
        }

        ProvedorIa provedor = ProvedorIa.builder()
                .nome(request.nome())
                .tipo(request.tipo())
                .modelo(request.modelo().trim())
                .baseUrl(textoOuNull(request.baseUrl()))
                .apiKeyCifrada(apiKey == null ? null : cifradorSegredos.cifrar(apiKey))
                .temperatura(request.temperatura())
                .maxTokens(request.maxTokens())
                .timeoutSegundos(request.timeoutSegundos())
                .padrao(request.padrao())
                .ativo(request.ativo())
                .build();
        return toResponse(provedorIaRepository.save(provedor));
    }

    @Transactional
    public ProvedorIaResponse atualizar(UUID id, ProvedorIaRequest request) {
        ProvedorIa provedor = buscarEntidade(id);
        validarDuplicidade(request.nome(), id);
        String novaApiKey = textoOuNull(request.apiKey());
        // OLLAMA não usa chave: ao trocar para ele, a chave antiga é descartada.
        boolean manterChave = novaApiKey == null && request.tipo().exigeApiKey() && provedor.getApiKeyCifrada() != null;
        validarPorTipo(request, novaApiKey != null || manterChave);
        if (request.padrao() && !provedor.isPadrao()) {
            desmarcarPadraoAtual();
        }

        provedor.setNome(request.nome());
        provedor.setTipo(request.tipo());
        provedor.setModelo(request.modelo().trim());
        provedor.setBaseUrl(textoOuNull(request.baseUrl()));
        if (novaApiKey != null) {
            provedor.setApiKeyCifrada(cifradorSegredos.cifrar(novaApiKey));
        } else if (!manterChave) {
            provedor.setApiKeyCifrada(null);
        }
        provedor.setTemperatura(request.temperatura());
        provedor.setMaxTokens(request.maxTokens());
        provedor.setTimeoutSegundos(request.timeoutSegundos());
        provedor.setPadrao(request.padrao());
        provedor.setAtivo(request.ativo());
        chatModelFactory.invalidar(id);
        return toResponse(provedor);
    }

    @Transactional
    public void excluir(UUID id) {
        ProvedorIa provedor = buscarEntidade(id);
        provedorIaRepository.delete(provedor);
        chatModelFactory.invalidar(id);
    }

    /**
     * Envia um prompt ao provedor e mede a resposta. Roda fora de transação para não segurar
     * conexão do banco durante a chamada externa. Erros do provedor não viram HTTP 500: voltam
     * com {@code sucesso=false} para a tela de teste exibir.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TesteIaResponse testar(UUID id, String prompt) {
        ProvedorIa provedor = buscarEntidade(id);
        String promptFinal = prompt == null || prompt.isBlank() ? PROMPT_PADRAO : prompt;

        long inicio = System.nanoTime();
        try {
            ChatModel chatModel = chatModelFactory.obter(provedor);
            ChatResponse resposta = chatModel.chat(ChatRequest.builder()
                    .messages(UserMessage.from(promptFinal))
                    .build());
            long latenciaMs = (System.nanoTime() - inicio) / 1_000_000;
            TokenUsage uso = resposta.tokenUsage();
            return new TesteIaResponse(
                    true,
                    promptFinal,
                    resposta.aiMessage() == null ? null : resposta.aiMessage().text(),
                    resposta.modelName() != null ? resposta.modelName() : provedor.getModelo(),
                    latenciaMs,
                    uso == null ? null : uso.inputTokenCount(),
                    uso == null ? null : uso.outputTokenCount(),
                    null);
        } catch (RuntimeException e) {
            long latenciaMs = (System.nanoTime() - inicio) / 1_000_000;
            log.warn("Falha ao testar provedor de IA {} ({}): {}", provedor.getId(), provedor.getTipo(), e.toString());
            return new TesteIaResponse(
                    false, promptFinal, null, provedor.getModelo(), latenciaMs, null, null, mensagemErro(e));
        }
    }

    /** Modelo do provedor padrão ativo do usuário, para as funcionalidades de IA do sistema. */
    public ChatModel chatModelPadrao() {
        ProvedorIa provedor = provedorIaRepository.findFirstByPadraoTrueAndAtivoTrue()
                .orElseThrow(() -> new ProvedorIaInvalidoException("Nenhum provedor de IA padrão ativo configurado"));
        return chatModelFactory.obter(provedor);
    }

    private ProvedorIa buscarEntidade(UUID id) {
        return provedorIaRepository.buscarPorId(id).orElseThrow(ProvedorIaNaoEncontradoException::new);
    }

    private ProvedorIaResponse toResponse(ProvedorIa provedor) {
        String apiKey = provedor.getApiKeyCifrada() == null ? null : cifradorSegredos.decifrar(provedor.getApiKeyCifrada());
        return ProvedorIaMapper.toResponse(provedor, apiKey);
    }

    private void desmarcarPadraoAtual() {
        provedorIaRepository.findByPadraoTrue().forEach(p -> p.setPadrao(false));
        // Grava antes do novo padrão para não violar o índice único parcial (um padrão por usuário).
        provedorIaRepository.flush();
    }

    private void validarPorTipo(ProvedorIaRequest request, boolean possuiApiKey) {
        if (request.tipo().exigeApiKey() && !possuiApiKey) {
            throw new ProvedorIaInvalidoException("Informe a API key para provedores do tipo " + request.tipo());
        }
        if (request.tipo().exigeBaseUrl() && textoOuNull(request.baseUrl()) == null) {
            throw new ProvedorIaInvalidoException("Informe a URL base para provedores do tipo " + request.tipo());
        }
    }

    private void validarDuplicidade(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? provedorIaRepository.existsByNomeIgnoreCase(nome)
                : provedorIaRepository.existsByNomeIgnoreCaseAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new ProvedorIaDuplicadoException(nome);
        }
    }

    private static String textoOuNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static String mensagemErro(RuntimeException e) {
        Throwable causa = e;
        while (causa.getMessage() == null && causa.getCause() != null) {
            causa = causa.getCause();
        }
        String detalhe = causa.getMessage() == null ? "" : extrairMensagemDoProvedor(causa.getMessage());
        String mensagem = causa.getClass().getSimpleName() + (detalhe.isEmpty() ? "" : ": " + detalhe);
        return mensagem.length() > TAMANHO_MAXIMO_ERRO ? mensagem.substring(0, TAMANHO_MAXIMO_ERRO) + "..." : mensagem;
    }

    /**
     * Os provedores devolvem o erro como JSON ({@code {"error": {"code": 503, "message": "..."}}});
     * quando for o caso, mostra só o código e a mensagem em vez do corpo inteiro.
     */
    private static String extrairMensagemDoProvedor(String corpo) {
        Matcher mensagem = MENSAGEM_JSON.matcher(corpo);
        if (!mensagem.find()) {
            return corpo.strip();
        }
        Matcher codigo = CODIGO_JSON.matcher(corpo);
        String texto = mensagem.group(1).replace("\\\"", "\"").replace("\\n", " ");
        return codigo.find() ? "[" + codigo.group(1) + "] " + texto : texto;
    }
}
