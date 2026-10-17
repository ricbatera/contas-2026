package br.com.rdrtech.ia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatModelFactoryTest {

    private final CifradorSegredos cifrador =
            new CifradorSegredos(new IaProperties("dGVzdC1vbmx5LWNoYXZlLWlhLTMyLWJ5dGVzLTEyMzQ="));
    private final ChatModelFactory factory = new ChatModelFactory(cifrador);

    private ProvedorIa provedor(TipoProvedorIa tipo) {
        ProvedorIa provedor = ProvedorIa.builder()
                .nome("Teste").tipo(tipo).modelo("modelo-x")
                .baseUrl(tipo == TipoProvedorIa.OLLAMA ? "http://localhost:11434" : null)
                .apiKeyCifrada(tipo.exigeApiKey() ? cifrador.cifrar("sk-teste-123456") : null)
                .temperatura(BigDecimal.valueOf(0.7)).maxTokens(256).timeoutSegundos(30)
                .ativo(true).build();
        provedor.setId(UUID.randomUUID());
        provedor.getAuditoria().setUpdatedAt(LocalDateTime.now());
        return provedor;
    }

    @Test
    void criar_deveMontarModeloDoTipoCorreto() {
        assertThat(factory.criar(provedor(TipoProvedorIa.OPENAI))).isInstanceOf(OpenAiChatModel.class);
        assertThat(factory.criar(provedor(TipoProvedorIa.ANTHROPIC))).isInstanceOf(AnthropicChatModel.class);
        assertThat(factory.criar(provedor(TipoProvedorIa.GEMINI))).isInstanceOf(GoogleAiGeminiChatModel.class);
        assertThat(factory.criar(provedor(TipoProvedorIa.OLLAMA))).isInstanceOf(OllamaChatModel.class);
    }

    @Test
    void criar_deveAceitarBaseUrlCustomizada_paraApisCompativeisComOpenAi() {
        ProvedorIa provedor = provedor(TipoProvedorIa.OPENAI);
        provedor.setBaseUrl("https://api.groq.com/openai/v1");

        assertThat(factory.criar(provedor)).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void obter_deveReutilizarModelo_enquantoProvedorNaoMudar() {
        ProvedorIa provedor = provedor(TipoProvedorIa.OPENAI);

        ChatModel primeiro = factory.obter(provedor);

        assertThat(factory.obter(provedor)).isSameAs(primeiro);
        provedor.getAuditoria().setUpdatedAt(LocalDateTime.now().plusSeconds(1));
        assertThat(factory.obter(provedor)).isNotSameAs(primeiro);
    }

    @Test
    void invalidar_deveDescartarModeloEmCache() {
        ProvedorIa provedor = provedor(TipoProvedorIa.ANTHROPIC);
        ChatModel primeiro = factory.obter(provedor);

        factory.invalidar(provedor.getId());

        assertThat(factory.obter(provedor)).isNotSameAs(primeiro);
    }
}
