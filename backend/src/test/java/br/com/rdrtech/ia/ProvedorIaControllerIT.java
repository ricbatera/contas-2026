package br.com.rdrtech.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.ia.dto.ModeloIaResponse;
import br.com.rdrtech.ia.dto.ModelosIaRequest;
import br.com.rdrtech.ia.dto.ProvedorIaRequest;
import br.com.rdrtech.ia.dto.TesteIaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProvedorIaControllerIT {

    private static final String API_KEY = "sk-proj-chave-de-teste-9876";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Evita chamadas reais aos provedores: o teste controla o {@link ChatModel} devolvido. */
    @MockitoBean
    private ChatModelFactory chatModelFactory;
    @MockitoBean
    private CatalogoModelosIa catalogoModelosIa;

    private String registrarERetornarToken(String prefixoEmail) throws Exception {
        String email = prefixoEmail + "-" + System.currentTimeMillis() + "-" + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest("Usuario", "Teste", email, "Senha123!", null);

        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private ProvedorIaRequest requestOpenAi(String nome, boolean padrao) {
        return new ProvedorIaRequest(nome, TipoProvedorIa.OPENAI, "gpt-4o-mini", null, API_KEY,
                BigDecimal.valueOf(0.5), 512, 60, padrao, true);
    }

    private String criarERetornarId(String token, ProvedorIaRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/ia/provedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    @Test
    void criar_deveRetornar201SemExporApiKey_eGravarChaveCifrada() throws Exception {
        String token = registrarERetornarToken("ia-cria");

        String body = mockMvc.perform(post("/api/ia/provedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestOpenAi("OpenAI", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nome").value("OpenAI"))
                .andExpect(jsonPath("$.data.tipo").value("OPENAI"))
                .andExpect(jsonPath("$.data.modelo").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.possuiApiKey").value(true))
                .andExpect(jsonPath("$.data.apiKeyMascarada").value("sk-...9876"))
                .andExpect(jsonPath("$.data.padrao").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString(API_KEY))))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(body).at("/data/id").asText());
        String gravada = jdbcTemplate.queryForObject(
                "select api_key_cifrada from provedor_ia where id = ?", String.class, id);
        assertThat(gravada).startsWith("v1:").doesNotContain(API_KEY);
    }

    @Test
    void criar_deveRetornar400_quandoOpenAiSemApiKey() throws Exception {
        String token = registrarERetornarToken("ia-semchave");
        ProvedorIaRequest request = new ProvedorIaRequest(
                "Sem chave", TipoProvedorIa.OPENAI, "gpt-4o-mini", null, null, null, null, 60, false, true);

        mockMvc.perform(post("/api/ia/provedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_deveRetornar400_quandoTemperaturaForaDoIntervalo() throws Exception {
        String token = registrarERetornarToken("ia-temp");
        ProvedorIaRequest request = new ProvedorIaRequest(
                "Quente", TipoProvedorIa.OPENAI, "gpt-4o-mini", null, API_KEY, BigDecimal.valueOf(3), null, 60, false, true);

        mockMvc.perform(post("/api/ia/provedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_deveRetornar409_quandoNomeDuplicado() throws Exception {
        String token = registrarERetornarToken("ia-dup");
        criarERetornarId(token, requestOpenAi("Duplicado", false));

        mockMvc.perform(post("/api/ia/provedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestOpenAi("duplicado", false))))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_deveManterUmUnicoPadrao_porUsuario() throws Exception {
        String token = registrarERetornarToken("ia-padrao");
        String primeiro = criarERetornarId(token, requestOpenAi("Primeiro", true));
        String segundo = criarERetornarId(token, requestOpenAi("Segundo", true));

        mockMvc.perform(get("/api/ia/provedores/" + primeiro).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.padrao").value(false));
        mockMvc.perform(get("/api/ia/provedores/" + segundo).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.padrao").value(true));
    }

    @Test
    void criar_deveAceitarOllamaSemApiKey() throws Exception {
        String token = registrarERetornarToken("ia-ollama");
        ProvedorIaRequest request = new ProvedorIaRequest("Local", TipoProvedorIa.OLLAMA, "llama3.2",
                "http://localhost:11434", null, null, null, 120, false, true);

        mockMvc.perform(post("/api/ia/provedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.possuiApiKey").value(false))
                .andExpect(jsonPath("$.data.baseUrl").value("http://localhost:11434"));
    }

    @Test
    void atualizar_deveManterApiKey_quandoEnviadaVazia() throws Exception {
        String token = registrarERetornarToken("ia-atualiza");
        String id = criarERetornarId(token, requestOpenAi("Antigo", false));
        ProvedorIaRequest request = new ProvedorIaRequest(
                "Novo nome", TipoProvedorIa.OPENAI, "gpt-4.1", null, "", null, null, 30, false, false);

        mockMvc.perform(put("/api/ia/provedores/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Novo nome"))
                .andExpect(jsonPath("$.data.modelo").value("gpt-4.1"))
                .andExpect(jsonPath("$.data.ativo").value(false))
                .andExpect(jsonPath("$.data.apiKeyMascarada").value("sk-...9876"));
    }

    @Test
    void excluir_deveRetornar204_eDepois404() throws Exception {
        String token = registrarERetornarToken("ia-exclui");
        String id = criarERetornarId(token, requestOpenAi("Excluir", false));

        mockMvc.perform(delete("/api/ia/provedores/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ia/provedores/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscarPorId_deveRetornar404_quandoProvedorDeOutroUsuario() throws Exception {
        String tokenDono = registrarERetornarToken("ia-dono");
        String tokenOutro = registrarERetornarToken("ia-outro");
        String id = criarERetornarId(tokenDono, requestOpenAi("Privado", false));

        mockMvc.perform(get("/api/ia/provedores/" + id).header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/ia/provedores/" + id + "/testar").header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ia/provedores").header("Authorization", "Bearer " + tokenOutro))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void testar_deveRetornarRespostaDoModelo() throws Exception {
        String token = registrarERetornarToken("ia-testa");
        String id = criarERetornarId(token, requestOpenAi("Testável", false));
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelFactory.obter(any(ProvedorIa.class))).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("Olá!"))
                .tokenUsage(new TokenUsage(5, 2))
                .build());

        mockMvc.perform(post("/api/ia/provedores/" + id + "/testar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TesteIaRequest("Diga olá"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sucesso").value(true))
                .andExpect(jsonPath("$.data.prompt").value("Diga olá"))
                .andExpect(jsonPath("$.data.resposta").value("Olá!"))
                .andExpect(jsonPath("$.data.modelo").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.tokensEntrada").value(5))
                .andExpect(jsonPath("$.data.tokensSaida").value(2));
    }

    @Test
    void testar_deveRetornar200ComSucessoFalso_quandoProvedorFalha() throws Exception {
        String token = registrarERetornarToken("ia-falha");
        String id = criarERetornarId(token, requestOpenAi("Falho", false));
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelFactory.obter(any(ProvedorIa.class))).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("Incorrect API key provided"));

        mockMvc.perform(post("/api/ia/provedores/" + id + "/testar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sucesso").value(false))
                .andExpect(jsonPath("$.data.erro").value(Matchers.containsString("Incorrect API key provided")));
    }

    private org.springframework.test.web.servlet.ResultActions listarModelos(String token, ModelosIaRequest request)
            throws Exception {
        return mockMvc.perform(post("/api/ia/provedores/modelos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    void listarModelos_deveRetornarModelosDoProvedor_comChaveInformada() throws Exception {
        String token = registrarERetornarToken("ia-modelos");
        when(catalogoModelosIa.listar(eq(TipoProvedorIa.OPENAI), isNull(), eq(API_KEY), any(Duration.class)))
                .thenReturn(List.of(new ModeloIaResponse("gpt-4o", "gpt-4o"), new ModeloIaResponse("gpt-4o-mini", "gpt-4o-mini")));

        listarModelos(token, new ModelosIaRequest(TipoProvedorIa.OPENAI, null, API_KEY, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelos.length()").value(2))
                .andExpect(jsonPath("$.data.modelos[0].nome").value("gpt-4o"))
                .andExpect(jsonPath("$.data.erro").doesNotExist());
    }

    @Test
    void listarModelos_deveUsarChaveGravada_quandoApiKeyVaziaNaEdicao() throws Exception {
        String token = registrarERetornarToken("ia-modelos-gravada");
        String id = criarERetornarId(token, requestOpenAi("Com chave", false));
        when(catalogoModelosIa.listar(eq(TipoProvedorIa.OPENAI), eq("https://api.groq.com/openai/v1"), eq(API_KEY),
                any(Duration.class)))
                .thenReturn(List.of(new ModeloIaResponse("llama-3.3-70b", "llama-3.3-70b")));

        listarModelos(token, new ModelosIaRequest(
                TipoProvedorIa.OPENAI, "https://api.groq.com/openai/v1", null, UUID.fromString(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelos[0].nome").value("llama-3.3-70b"))
                .andExpect(content().string(Matchers.not(Matchers.containsString(API_KEY))));
    }

    @Test
    void listarModelos_deveRetornar200ComErro_quandoProvedorFalha() throws Exception {
        String token = registrarERetornarToken("ia-modelos-falha");
        when(catalogoModelosIa.listar(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("{\"error\": {\"code\": 401, \"message\": \"Invalid API key\"}}"));

        listarModelos(token, new ModelosIaRequest(TipoProvedorIa.GEMINI, null, "chave-errada", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelos.length()").value(0))
                .andExpect(jsonPath("$.data.erro").value(Matchers.containsString("[401] Invalid API key")));
    }

    @Test
    void listarModelos_deveRetornar400_quandoSemChaveOuSemUrlDoOllama() throws Exception {
        String token = registrarERetornarToken("ia-modelos-400");

        listarModelos(token, new ModelosIaRequest(TipoProvedorIa.ANTHROPIC, null, null, null))
                .andExpect(status().isBadRequest());
        listarModelos(token, new ModelosIaRequest(TipoProvedorIa.OLLAMA, null, null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarModelos_deveRetornar404_quandoProvedorDeOutroUsuario() throws Exception {
        String tokenDono = registrarERetornarToken("ia-modelos-dono");
        String tokenOutro = registrarERetornarToken("ia-modelos-outro");
        String id = criarERetornarId(tokenDono, requestOpenAi("Chave alheia", false));

        listarModelos(tokenOutro, new ModelosIaRequest(TipoProvedorIa.OPENAI, null, null, UUID.fromString(id)))
                .andExpect(status().isNotFound());
    }
}
