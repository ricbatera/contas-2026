package br.com.rdrtech.comprovante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.rdrtech.exception.ComprovanteInvalidoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LeitorComprovanteIaTest {

    private final LeitorComprovanteIa leitor = new LeitorComprovanteIa(new ObjectMapper());

    @Test
    void deveInterpretarJsonComCercaDeMarkdown() {
        ComprovanteLidoIa lido = leitor.interpretar("""
                Aqui está:
                ```json
                {"legivel": true, "estabelecimento": "PADARIA", "data": "2026-03-02", "valorTotal": 18.5,
                 "modalidade": "DEBITO", "parcelas": 1, "ultimosDigitos": "1234", "bandeira": "ELO",
                 "categoriaId": null, "subcategoriaId": null}
                ```
                """);

        assertThat(lido.estabelecimento()).isEqualTo("PADARIA");
        assertThat(lido.valorTotal()).isEqualByComparingTo("18.50");
        assertThat(lido.modalidade()).isEqualTo("DEBITO");
    }

    @Test
    void deveRecusarRespostaSemJson() {
        assertThatThrownBy(() -> leitor.interpretar("não consegui ler"))
                .isInstanceOf(ComprovanteInvalidoException.class);
        assertThatThrownBy(() -> leitor.interpretar(" "))
                .isInstanceOf(ComprovanteInvalidoException.class);
    }

    @Test
    void deveEnviarImagemETentarDeNovoQuandoOJsonVemInvalido() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                resposta("sem json"),
                resposta("{\"estabelecimento\": \"BAR\", \"valorTotal\": 40, \"parcelas\": 2}"));

        LeitorComprovanteIa.Leitura leitura = leitor.ler(
                chatModel, new byte[] {1, 2, 3}, "image/jpeg", List.of(), LocalDate.of(2026, 3, 10));

        assertThat(leitura.comprovante().estabelecimento()).isEqualTo("BAR");
        assertThat(leitura.comprovante().parcelas()).isEqualTo(2);
        assertThat(leitura.respostaBruta()).contains("sem json").contains("nova tentativa");
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(captor.capture());
        UserMessage mensagem = (UserMessage) captor.getValue().messages().get(1);
        assertThat(mensagem.contents()).anySatisfy(conteudo -> {
            assertThat(conteudo).isInstanceOf(ImageContent.class);
            assertThat(((ImageContent) conteudo).image().mimeType()).isEqualTo("image/jpeg");
        });
    }

    @Test
    void deveFalharDepoisDeDuasRespostasInvalidas() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(resposta("nada"));

        assertThatThrownBy(() -> leitor.ler(chatModel, new byte[] {1}, "image/png", List.of(), LocalDate.now()))
                .isInstanceOf(ComprovanteInvalidoException.class);
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    private static ChatResponse resposta(String texto) {
        return ChatResponse.builder().aiMessage(AiMessage.from(texto)).modelName("teste").build();
    }
}
