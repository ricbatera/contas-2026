package br.com.rdrtech.ia;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Monta o {@link ChatModel} do LangChain4j a partir do cadastro em {@link ProvedorIa}. Os starters
 * Spring Boot do LangChain4j não são usados porque leem a configuração uma única vez no boot; aqui
 * cada provedor é construído em runtime pelos builders. O modelo fica em cache por provedor até o
 * registro mudar ({@code updatedAt}) ou ser excluído.
 */
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    /**
     * Sem novas tentativas: com o modelo sobrecarregado (503) ou lento, cada tentativa espera o
     * timeout inteiro e a tela fica parada pelo dobro do tempo. Quem chama decide se tenta de novo.
     */
    private static final int MAX_RETRIES = 0;

    private final CifradorSegredos cifradorSegredos;
    private final Map<UUID, ModeloEmCache> cache = new ConcurrentHashMap<>();

    public ChatModel obter(ProvedorIa provedor) {
        LocalDateTime versao = provedor.getAuditoria().getUpdatedAt();
        ModeloEmCache emCache = cache.get(provedor.getId());
        if (emCache != null && Objects.equals(emCache.versao(), versao)) {
            return emCache.modelo();
        }
        ChatModel modelo = criar(provedor);
        if (provedor.getId() != null) {
            cache.put(provedor.getId(), new ModeloEmCache(versao, modelo));
        }
        return modelo;
    }

    public void invalidar(UUID provedorId) {
        cache.remove(provedorId);
    }

    ChatModel criar(ProvedorIa provedor) {
        String apiKey = provedor.getApiKeyCifrada() == null ? null : cifradorSegredos.decifrar(provedor.getApiKeyCifrada());
        String baseUrl = provedor.getBaseUrl() == null || provedor.getBaseUrl().isBlank() ? null : provedor.getBaseUrl();
        Double temperatura = provedor.getTemperatura() == null ? null : provedor.getTemperatura().doubleValue();
        Duration timeout = Duration.ofSeconds(provedor.getTimeoutSegundos());

        return switch (provedor.getTipo()) {
            case OPENAI -> {
                var builder = OpenAiChatModel.builder()
                        .apiKey(apiKey).modelName(provedor.getModelo()).temperature(temperatura)
                        .maxTokens(provedor.getMaxTokens()).timeout(timeout).maxRetries(MAX_RETRIES);
                if (baseUrl != null) {
                    builder.baseUrl(baseUrl);
                }
                yield builder.build();
            }
            case ANTHROPIC -> {
                var builder = AnthropicChatModel.builder()
                        .apiKey(apiKey).modelName(provedor.getModelo()).temperature(temperatura)
                        .maxTokens(provedor.getMaxTokens()).timeout(timeout).maxRetries(MAX_RETRIES);
                if (baseUrl != null) {
                    builder.baseUrl(baseUrl);
                }
                yield builder.build();
            }
            case GEMINI -> {
                var builder = GoogleAiGeminiChatModel.builder()
                        .apiKey(apiKey).modelName(provedor.getModelo()).temperature(temperatura)
                        .maxOutputTokens(provedor.getMaxTokens()).timeout(timeout).maxRetries(MAX_RETRIES);
                if (baseUrl != null) {
                    builder.baseUrl(baseUrl);
                }
                yield builder.build();
            }
            case OLLAMA -> OllamaChatModel.builder()
                    .baseUrl(baseUrl).modelName(provedor.getModelo()).temperature(temperatura)
                    .numPredict(provedor.getMaxTokens()).timeout(timeout).maxRetries(MAX_RETRIES)
                    .build();
        };
    }

    private record ModeloEmCache(LocalDateTime versao, ChatModel modelo) {
    }
}
