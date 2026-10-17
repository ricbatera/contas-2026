package br.com.rdrtech.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.rdrtech.exception.LimiteProvedoresIaExcedidoException;
import br.com.rdrtech.exception.ProvedorIaDuplicadoException;
import br.com.rdrtech.exception.ProvedorIaInvalidoException;
import br.com.rdrtech.exception.ProvedorIaNaoEncontradoException;
import br.com.rdrtech.ia.dto.ProvedorIaRequest;
import br.com.rdrtech.ia.dto.ProvedorIaResponse;
import br.com.rdrtech.ia.dto.TesteIaResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvedorIaServiceTest {

    @Mock
    private ProvedorIaRepository provedorIaRepository;
    @Mock
    private ChatModelFactory chatModelFactory;

    private final CifradorSegredos cifrador =
            new CifradorSegredos(new IaProperties("dGVzdC1vbmx5LWNoYXZlLWlhLTMyLWJ5dGVzLTEyMzQ="));
    private ProvedorIaService provedorIaService;

    @BeforeEach
    void setUp() {
        provedorIaService = new ProvedorIaService(provedorIaRepository, cifrador, chatModelFactory);
    }

    private ProvedorIaRequest request(TipoProvedorIa tipo, String apiKey, String baseUrl, boolean padrao) {
        return new ProvedorIaRequest("Meu provedor", tipo, "modelo-x", baseUrl, apiKey, null, null, 60, padrao, true);
    }

    private ProvedorIa existente(UUID id, String apiKey, boolean padrao) {
        ProvedorIa provedor = ProvedorIa.builder()
                .nome("Meu provedor").tipo(TipoProvedorIa.OPENAI).modelo("gpt-x")
                .apiKeyCifrada(apiKey == null ? null : cifrador.cifrar(apiKey))
                .timeoutSegundos(60).padrao(padrao).ativo(true).build();
        provedor.setId(id);
        return provedor;
    }

    @Test
    void criar_deveCifrarApiKey_eNaoDevolverChaveCompleta() {
        when(provedorIaRepository.save(any(ProvedorIa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProvedorIaResponse response = provedorIaService.criar(
                request(TipoProvedorIa.OPENAI, "sk-proj-1234567890abcd", null, false));

        ArgumentCaptor<ProvedorIa> captor = ArgumentCaptor.forClass(ProvedorIa.class);
        verify(provedorIaRepository).save(captor.capture());
        assertThat(captor.getValue().getApiKeyCifrada()).startsWith("v1:");
        assertThat(cifrador.decifrar(captor.getValue().getApiKeyCifrada())).isEqualTo("sk-proj-1234567890abcd");
        assertThat(response.possuiApiKey()).isTrue();
        assertThat(response.apiKeyMascarada()).isEqualTo("sk-...abcd");
    }

    @Test
    void criar_deveExigirApiKey_quandoTipoNaoForOllama() {
        assertThatThrownBy(() -> provedorIaService.criar(request(TipoProvedorIa.ANTHROPIC, "  ", null, false)))
                .isInstanceOf(ProvedorIaInvalidoException.class);
        verify(provedorIaRepository, never()).save(any());
    }

    @Test
    void criar_deveExigirBaseUrl_quandoTipoOllama() {
        assertThatThrownBy(() -> provedorIaService.criar(request(TipoProvedorIa.OLLAMA, null, null, false)))
                .isInstanceOf(ProvedorIaInvalidoException.class);
    }

    @Test
    void criar_deveLancarDuplicado_quandoNomeJaExiste() {
        when(provedorIaRepository.existsByNomeIgnoreCase("Meu provedor")).thenReturn(true);

        assertThatThrownBy(() -> provedorIaService.criar(request(TipoProvedorIa.OPENAI, "sk-1", null, false)))
                .isInstanceOf(ProvedorIaDuplicadoException.class);
    }

    @Test
    void criar_deveLancarLimite_quandoJaTem20Provedores() {
        when(provedorIaRepository.count()).thenReturn(20L);

        assertThatThrownBy(() -> provedorIaService.criar(request(TipoProvedorIa.OPENAI, "sk-1", null, false)))
                .isInstanceOf(LimiteProvedoresIaExcedidoException.class);
    }

    @Test
    void criar_deveDesmarcarPadraoAnterior_quandoNovoForPadrao() {
        ProvedorIa anterior = existente(UUID.randomUUID(), "sk-antigo-123456", true);
        when(provedorIaRepository.findByPadraoTrue()).thenReturn(List.of(anterior));
        when(provedorIaRepository.save(any(ProvedorIa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProvedorIaResponse response = provedorIaService.criar(request(TipoProvedorIa.OPENAI, "sk-1", null, true));

        assertThat(anterior.isPadrao()).isFalse();
        assertThat(response.padrao()).isTrue();
        verify(provedorIaRepository).flush();
    }

    @Test
    void atualizar_deveManterApiKey_quandoNaoInformada() {
        UUID id = UUID.randomUUID();
        ProvedorIa provedor = existente(id, "sk-original-123456", false);
        String cifradaOriginal = provedor.getApiKeyCifrada();
        when(provedorIaRepository.buscarPorId(id)).thenReturn(Optional.of(provedor));

        provedorIaService.atualizar(id, request(TipoProvedorIa.OPENAI, "", null, false));

        assertThat(provedor.getApiKeyCifrada()).isEqualTo(cifradaOriginal);
        verify(chatModelFactory).invalidar(id);
    }

    @Test
    void atualizar_deveDescartarApiKey_quandoMudarParaOllama() {
        UUID id = UUID.randomUUID();
        ProvedorIa provedor = existente(id, "sk-original-123456", false);
        when(provedorIaRepository.buscarPorId(id)).thenReturn(Optional.of(provedor));

        provedorIaService.atualizar(id, request(TipoProvedorIa.OLLAMA, null, "http://localhost:11434", false));

        assertThat(provedor.getApiKeyCifrada()).isNull();
        assertThat(provedor.getTipo()).isEqualTo(TipoProvedorIa.OLLAMA);
    }

    @Test
    void buscarPorId_deveLancarNaoEncontrado_quandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(provedorIaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provedorIaService.buscarPorId(id)).isInstanceOf(ProvedorIaNaoEncontradoException.class);
    }

    @Test
    void testar_deveRetornarRespostaETokens_quandoProvedorResponde() {
        UUID id = UUID.randomUUID();
        ProvedorIa provedor = existente(id, "sk-original-123456", false);
        ChatModel chatModel = mock(ChatModel.class);
        when(provedorIaRepository.buscarPorId(id)).thenReturn(Optional.of(provedor));
        when(chatModelFactory.obter(provedor)).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("OK"))
                .modelName("gpt-x-2026")
                .tokenUsage(new TokenUsage(12, 1))
                .build());

        TesteIaResponse response = provedorIaService.testar(id, null);

        assertThat(response.sucesso()).isTrue();
        assertThat(response.prompt()).isEqualTo(ProvedorIaService.PROMPT_PADRAO);
        assertThat(response.resposta()).isEqualTo("OK");
        assertThat(response.modelo()).isEqualTo("gpt-x-2026");
        assertThat(response.tokensEntrada()).isEqualTo(12);
        assertThat(response.tokensSaida()).isEqualTo(1);
        assertThat(response.erro()).isNull();
    }

    @Test
    void mensagemErro_deveResumirErroJsonDoProvedor() {
        String corpo = """
                {
                  "error": {
                    "code": 503,
                    "message": "This model is currently experiencing high demand.",
                    "status": "UNAVAILABLE"
                  }
                }
                """;

        assertThat(ProvedorIaService.mensagemErro(new RuntimeException(corpo)))
                .isEqualTo("RuntimeException: [503] This model is currently experiencing high demand.");
        assertThat(ProvedorIaService.mensagemErro(new RuntimeException("request timed out")))
                .isEqualTo("RuntimeException: request timed out");
    }

    @Test
    void testar_deveRetornarSucessoFalso_quandoProvedorFalha() {
        UUID id = UUID.randomUUID();
        ProvedorIa provedor = existente(id, "sk-invalida-123456", false);
        ChatModel chatModel = mock(ChatModel.class);
        when(provedorIaRepository.buscarPorId(id)).thenReturn(Optional.of(provedor));
        when(chatModelFactory.obter(provedor)).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("401 Unauthorized"));

        TesteIaResponse response = provedorIaService.testar(id, "Olá");

        assertThat(response.sucesso()).isFalse();
        assertThat(response.resposta()).isNull();
        assertThat(response.erro()).contains("401 Unauthorized");
    }
}
