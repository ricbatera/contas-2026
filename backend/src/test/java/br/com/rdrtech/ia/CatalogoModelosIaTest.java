package br.com.rdrtech.ia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.rdrtech.ia.dto.ModeloIaResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.catalog.ModelType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogoModelosIaTest {

    private static ModelDescription modelo(String nome, String exibicao, ModelType tipo) {
        return ModelDescription.builder().provider(ModelProvider.GOOGLE_AI_GEMINI).name(nome).displayName(exibicao).type(tipo).build();
    }

    @Test
    void converter_deveManterSoChatOrdenadoESemPrefixoDoGemini() {
        List<ModeloIaResponse> modelos = CatalogoModelosIa.converter(List.of(
                modelo("models/gemini-2.5-pro", "Gemini 2.5 Pro", ModelType.CHAT),
                modelo("models/text-embedding-004", "Embedding", ModelType.EMBEDDING),
                modelo("gpt-4o", null, null),
                modelo("gpt-4o", "repetido", ModelType.CHAT),
                modelo("dall-e-3", null, ModelType.IMAGE_GENERATION)));

        assertThat(modelos).containsExactly(
                new ModeloIaResponse("gemini-2.5-pro", "Gemini 2.5 Pro"),
                new ModeloIaResponse("gpt-4o", "gpt-4o"));
    }
}
