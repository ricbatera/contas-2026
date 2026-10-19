package br.com.rdrtech.comprovante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import br.com.rdrtech.cartao.Bandeira;
import br.com.rdrtech.cartao.TipoCartao;
import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.comprovante.dto.ComprovanteEdicaoRequest;
import br.com.rdrtech.comprovante.dto.ComprovanteRequest;
import br.com.rdrtech.ia.ChatModelFactory;
import br.com.rdrtech.ia.ProvedorIa;
import br.com.rdrtech.ia.TipoProvedorIa;
import br.com.rdrtech.ia.dto.ProvedorIaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ComprovanteControllerIT {

    /** Cabeçalho de um JPEG; o backend só confere o tipo, quem lê a imagem é a "IA" simulada. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatModelFactory chatModelFactory;

    private final List<ChatRequest> consultas = new ArrayList<>();

    @Test
    void deveLerComprovanteParceladoIdentificarCartaoECalcularVencimentoPelosDiasDoCartao() throws Exception {
        String token = registrar("comprovante");
        Referencias ref = criarReferencias(token);
        responderIa(jsonComprovante(ref, "LOJA DO ZE", "2026-03-20", "300.00", "CREDITO", 3, "************2222", "VISA"));

        JsonNode comprovante = enviar(token);

        assertThat(comprovante.at("/status").asText()).isEqualTo("PENDENTE");
        assertThat(comprovante.at("/erroLeitura").isNull()).isTrue();
        assertThat(comprovante.at("/descricao").asText()).isEqualTo("LOJA DO ZE");
        assertThat(comprovante.at("/modalidade").asText()).isEqualTo("CREDITO");
        assertThat(comprovante.at("/quantidadeParcelas").asInt()).isEqualTo(3);
        assertThat(comprovante.at("/valorPorParcela").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(comprovante.at("/ultimosDigitosLidos").asText()).isEqualTo("2222");
        // O adicional entra na fatura do principal, que fecha no dia 15 e vence no dia 25.
        assertThat(comprovante.at("/cartaoId").asText()).isEqualTo(ref.adicionalId());
        assertThat(comprovante.at("/primeiroVencimento").asText()).isEqualTo("2026-04-25");
        assertThat(comprovante.at("/subcategoriaId").asText()).isEqualTo(ref.subcategoriaId());

        UserMessage mensagem = (UserMessage) consultas.getFirst().messages().get(1);
        assertThat(mensagem.contents()).anyMatch(conteudo -> conteudo instanceof ImageContent);

        mockMvc.perform(get("/api/comprovantes/" + comprovante.at("/id").asText() + "/imagem")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));

        String aprovado = mockMvc.perform(post("/api/comprovantes/" + comprovante.at("/id").asText() + "/aprovar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APROVADO"))
                .andReturn().getResponse().getContentAsString();
        String saidaId = objectMapper.readTree(aprovado).at("/data/saidaId").asText();
        mockMvc.perform(get("/api/saidas/" + saidaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formaPagamento").value("CARTAO_CREDITO"))
                .andExpect(jsonPath("$.data.descricaoFatura").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.valorPorParcela").value(100.00))
                .andExpect(jsonPath("$.data.parcelas.length()").value(3))
                .andExpect(jsonPath("$.data.parcelas[0].vencimento").value("2026-04-25"))
                .andExpect(jsonPath("$.data.parcelas[2].vencimento").value("2026-06-25"));
    }

    @Test
    void deveGravarComprovanteMesmoQuandoAIaFalha() throws Exception {
        String token = registrar("comprovante-falha");
        criarReferencias(token);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelFactory.obter(any(ProvedorIa.class))).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

        JsonNode comprovante = enviar(token);

        assertThat(comprovante.at("/status").asText()).isEqualTo("PENDENTE");
        assertThat(comprovante.at("/erroLeitura").asText()).contains("Falha ao consultar o provedor de IA");
        assertThat(comprovante.at("/descricao").isNull()).isTrue();
    }

    @Test
    void deveRecusarArquivoQueNaoEImagem() throws Exception {
        String token = registrar("comprovante-invalido");
        criarReferencias(token);
        mockMvc.perform(post("/api/comprovantes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ComprovanteRequest("nota.pdf",
                                Base64.getEncoder().encodeToString("%PDF-1.7".getBytes())))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deveEditarEAprovarComprovanteDeDebito() throws Exception {
        String token = registrar("comprovante-debito");
        Referencias ref = criarReferencias(token);
        responderIa(jsonComprovante(ref, "PADARIA", "2026-03-02", "18.50", "DEBITO", null, null, null));
        JsonNode comprovante = enviar(token);
        String id = comprovante.at("/id").asText();
        assertThat(comprovante.at("/modalidade").asText()).isEqualTo("DEBITO");
        assertThat(comprovante.at("/cartaoId").isNull()).isTrue();
        assertThat(comprovante.at("/primeiroVencimento").isNull()).isTrue();

        ComprovanteEdicaoRequest edicao = new ComprovanteEdicaoRequest(ModalidadeComprovante.DEBITO, "Pão da manhã",
                null, UUID.fromString(ref.categoriaId()), UUID.fromString(ref.subcategoriaId()),
                LocalDate.of(2026, 3, 2), new BigDecimal("19.90"), 4, null);
        mockMvc.perform(put("/api/comprovantes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edicao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.descricao").value("Pão da manhã"))
                .andExpect(jsonPath("$.data.quantidadeParcelas").value(1));

        String aprovado = mockMvc.perform(post("/api/comprovantes/" + id + "/aprovar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String saidaId = objectMapper.readTree(aprovado).at("/data/saidaId").asText();
        mockMvc.perform(get("/api/saidas/" + saidaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formaPagamento").value("DEBITO"))
                .andExpect(jsonPath("$.data.dataInicial").value("2026-03-02"))
                .andExpect(jsonPath("$.data.valorPorParcela").value(19.90))
                .andExpect(jsonPath("$.data.parcelas.length()").value(1));

        // Aprovado não volta a ser editado nem pode ser excluído.
        mockMvc.perform(post("/api/comprovantes/" + id + "/rejeitar").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/comprovantes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void deveExigirCartaoEVencimentoParaAprovarCreditoSemCartaoIdentificado() throws Exception {
        String token = registrar("comprovante-sem-cartao");
        Referencias ref = criarReferencias(token);
        responderIa(jsonComprovante(ref, "POSTO", "2026-03-10", "120.00", "CREDITO", 1, "5555", "ELO"));
        JsonNode comprovante = enviar(token);
        String id = comprovante.at("/id").asText();
        assertThat(comprovante.at("/cartaoId").isNull()).isTrue();
        assertThat(comprovante.at("/avisos").toString()).contains("5555");

        mockMvc.perform(post("/api/comprovantes/" + id + "/aprovar").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // Escolher o cartão sem informar o vencimento recalcula pelos dias do cartão.
        ComprovanteEdicaoRequest edicao = new ComprovanteEdicaoRequest(ModalidadeComprovante.CREDITO, "Gasolina",
                UUID.fromString(ref.principalId()), UUID.fromString(ref.categoriaId()),
                UUID.fromString(ref.subcategoriaId()), LocalDate.of(2026, 3, 10), new BigDecimal("120.00"), 1, null);
        mockMvc.perform(put("/api/comprovantes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edicao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primeiroVencimento").value("2026-03-25"));
    }

    @Test
    void deveRejeitarReabrirEListarPendentesPrimeiro() throws Exception {
        String token = registrar("comprovante-rejeita");
        Referencias ref = criarReferencias(token);
        responderIa(jsonComprovante(ref, "BAR", "2026-03-10", "40.00", "CREDITO", 1, "1111", "VISA"));
        String primeiro = enviar(token).at("/id").asText();
        String segundo = enviar(token).at("/id").asText();

        mockMvc.perform(post("/api/comprovantes/" + primeiro + "/rejeitar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJEITADO"));
        mockMvc.perform(get("/api/comprovantes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(segundo))
                .andExpect(jsonPath("$.data[1].status").value("REJEITADO"));
        mockMvc.perform(get("/api/comprovantes?status=PENDENTE").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(post("/api/comprovantes/" + primeiro + "/reabrir").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDENTE"));

        // A mesma imagem enviada de novo gera aviso de possível duplicidade.
        mockMvc.perform(get("/api/comprovantes/" + segundo).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.avisos[0]").value(org.hamcrest.Matchers.containsString("já foi enviada")));
        mockMvc.perform(delete("/api/comprovantes/" + segundo).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void naoDeveExporComprovanteDeOutroUsuario() throws Exception {
        String tokenA = registrar("comprovante-a");
        Referencias ref = criarReferencias(tokenA);
        responderIa(jsonComprovante(ref, "LOJA", "2026-03-10", "10.00", "CREDITO", 1, "1111", "VISA"));
        String id = enviar(tokenA).at("/id").asText();
        String tokenB = registrar("comprovante-b");

        mockMvc.perform(get("/api/comprovantes/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/comprovantes/" + id + "/imagem").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/comprovantes").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- apoio ---

    private record Referencias(String categoriaId, String subcategoriaId, String principalId, String adicionalId) {
    }

    private String registrar(String prefixo) throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Ana", "Silva", prefixo + "-" + System.nanoTime() + "@example.com", "Senha123!", null);
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private String criar(String token, String path, Object request) throws Exception {
        String body = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    private Referencias criarReferencias(String token) throws Exception {
        String categoriaId = criar(token, "/api/categorias",
                new CategoriaRequest("Compras na maquininha", TipoCategoria.DESPESA, true));
        String subcategoriaId = criar(token, "/api/categorias/" + categoriaId + "/subcategorias",
                new SubcategoriaCreateRequest("Lojas", true));
        String principalId = criar(token, "/api/cartoes", new CartaoRequest(
                "Principal", "Banco", Bandeira.VISA, "1111", null, null, false, null, true, 15, 25));
        String adicionalId = criar(token, "/api/cartoes", new CartaoRequest(
                "Adicional", "Banco", Bandeira.VISA, "2222", null, TipoCartao.ADICIONAL, true,
                UUID.fromString(principalId)));
        criar(token, "/api/ia/provedores", new ProvedorIaRequest("Teste", TipoProvedorIa.OPENAI, "gpt-4o-mini",
                null, "sk-teste-1234", null, null, 60, true, true));
        return new Referencias(categoriaId, subcategoriaId, principalId, adicionalId);
    }

    private void responderIa(String resposta) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelFactory.obter(any(ProvedorIa.class))).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocacao -> {
            consultas.add(invocacao.getArgument(0));
            return ChatResponse.builder().aiMessage(AiMessage.from(resposta)).modelName("gpt-teste").build();
        });
    }

    private String jsonComprovante(Referencias ref, String estabelecimento, String data, String valor,
            String modalidade, Integer parcelas, String digitos, String bandeira) throws Exception {
        var json = new java.util.LinkedHashMap<String, Object>();
        json.put("legivel", true);
        json.put("estabelecimento", estabelecimento);
        json.put("data", data);
        json.put("valorTotal", new BigDecimal(valor));
        json.put("modalidade", modalidade);
        json.put("parcelas", parcelas);
        json.put("ultimosDigitos", digitos);
        json.put("bandeira", bandeira);
        json.put("categoriaId", ref.categoriaId());
        json.put("subcategoriaId", ref.subcategoriaId());
        return "```json\n" + objectMapper.writeValueAsString(json) + "\n```";
    }

    private JsonNode enviar(String token) throws Exception {
        String body = mockMvc.perform(post("/api/comprovantes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ComprovanteRequest("foto.jpg",
                                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data");
    }
}
