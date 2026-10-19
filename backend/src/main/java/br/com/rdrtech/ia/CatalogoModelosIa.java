package br.com.rdrtech.ia;

import br.com.rdrtech.ia.dto.ModeloIaResponse;
import dev.langchain4j.model.anthropic.AnthropicModelCatalog;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.catalog.ModelType;
import dev.langchain4j.model.googleai.GoogleAiGeminiModelCatalog;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Consulta no provedor os modelos disponíveis para a chave informada, usando os catálogos do
 * LangChain4j. Fica separado do {@link ProvedorIaService} para ser substituído nos testes.
 */
@Component
public class CatalogoModelosIa {

    private static final String PREFIXO_GEMINI = "models/";

    public List<ModeloIaResponse> listar(TipoProvedorIa tipo, String baseUrl, String apiKey, Duration timeout) {
        if (tipo == TipoProvedorIa.OLLAMA) {
            var modelos = OllamaModels.builder().baseUrl(baseUrl).timeout(timeout).maxRetries(0).build()
                    .availableModels().content();
            return ordenar(modelos.stream().map(m -> new ModeloIaResponse(m.getName(), m.getName())).toList());
        }
        return converter(catalogo(tipo, baseUrl, apiKey, timeout).listModels());
    }

    private static ModelCatalog catalogo(TipoProvedorIa tipo, String baseUrl, String apiKey, Duration timeout) {
        return switch (tipo) {
            case OPENAI -> {
                var builder = OpenAiModelCatalog.builder().apiKey(apiKey).readTimeout(timeout);
                if (baseUrl != null) {
                    builder.baseUrl(baseUrl);
                }
                yield builder.build();
            }
            case ANTHROPIC -> {
                var builder = AnthropicModelCatalog.builder().apiKey(apiKey).timeout(timeout);
                if (baseUrl != null) {
                    builder.baseUrl(baseUrl);
                }
                yield builder.build();
            }
            case GEMINI -> {
                var builder = GoogleAiGeminiModelCatalog.builder().apiKey(apiKey).timeout(timeout);
                if (baseUrl != null) {
                    builder.baseUrl(baseUrl);
                }
                yield builder.build();
            }
            case OLLAMA -> throw new IllegalArgumentException("Ollama não usa ModelCatalog");
        };
    }

    /**
     * Mantém só modelos de chat (o tipo nulo fica, pois nem todo provedor o informa), tira o
     * prefixo {@code models/} do Gemini e remove repetidos.
     */
    static List<ModeloIaResponse> converter(List<ModelDescription> descricoes) {
        Map<String, ModeloIaResponse> porNome = new LinkedHashMap<>();
        for (ModelDescription descricao : descricoes) {
            if (descricao.type() != null && descricao.type() != ModelType.CHAT) {
                continue;
            }
            String nome = semPrefixo(descricao.name());
            if (nome == null || nome.isBlank()) {
                continue;
            }
            String exibicao = descricao.displayName() == null || descricao.displayName().isBlank()
                    ? nome : descricao.displayName();
            porNome.putIfAbsent(nome, new ModeloIaResponse(nome, exibicao));
        }
        return ordenar(List.copyOf(porNome.values()));
    }

    private static String semPrefixo(String nome) {
        return nome != null && nome.startsWith(PREFIXO_GEMINI) ? nome.substring(PREFIXO_GEMINI.length()) : nome;
    }

    private static List<ModeloIaResponse> ordenar(List<ModeloIaResponse> modelos) {
        return modelos.stream().sorted(Comparator.comparing(ModeloIaResponse::nome)).toList();
    }
}
