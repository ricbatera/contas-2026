package br.com.rdrtech.faturaimportacao;

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
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoRequest;
import br.com.rdrtech.ia.ChatModelFactory;
import br.com.rdrtech.ia.ProvedorIa;
import br.com.rdrtech.ia.TipoProvedorIa;
import br.com.rdrtech.ia.dto.ProvedorIaRequest;
import br.com.rdrtech.presaida.dto.PreSaidaRequest;
import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
class FaturaImportacaoControllerIT {

    private static final String SENHA_PDF = "12345";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RecuperacaoImportacoesInterrompidas recuperacao;

    /** A leitura da fatura é simulada: o teste define o JSON que a "IA" devolve. */
    @MockitoBean
    private ChatModelFactory chatModelFactory;

    private final YearMonth competencia = YearMonth.now();
    private final LocalDate vencimento = competencia.atDay(10);

    @Test
    void deveConciliarExistentesCriarPreSaidasEAprovarParceladaComParcelasRetroativas() throws Exception {
        String token = registrar("importa");
        Referencias ref = criarReferencias(token);
        String saidaExistenteId = criar(token, "/api/saidas", compraAVista(ref, "Mercado", "50.00"));
        responderIa(jsonFatura(ref,
                item("1111", "MERCADO EXTRA 123", "50.00", 1, 1, "COMPRA", false),
                item("2222", "LOJA X PARC 03/10", "100.00", 3, 10, "COMPRA", true),
                item("9999", "UBER TRIP", "20.00", 1, 1, "COMPRA", false),
                item(null, "PAGAMENTO EFETUADO", "300.00", 1, 1, "PAGAMENTO", false)));

        JsonNode importacao = importar(token, pdf(SENHA_PDF), SENHA_PDF);

        assertThat(importacao.at("/status").asText()).isEqualTo("CONCLUIDA");
        assertThat(importacao.at("/cartaoPrincipalId").asText()).isEqualTo(ref.principalId());
        assertThat(importacao.at("/competencia").asInt()).isEqualTo(competencia.getYear() * 100 + competencia.getMonthValue());
        assertThat(importacao.at("/resumo/conciliados")).hasSize(1);
        assertThat(importacao.at("/resumo/conciliados/0/saidaId").asText()).isEqualTo(saidaExistenteId);
        assertThat(importacao.at("/resumo/ignorados")).hasSize(1);
        assertThat(importacao.at("/resumo/avisos").toString()).contains("9999");
        assertThat(importacao.at("/preSaidas")).hasSize(2);

        String descricaoFatura = jdbcTemplate.queryForObject(
                "select descricao_fatura from saida where id = ?", String.class, UUID.fromString(saidaExistenteId));
        assertThat(descricaoFatura).isEqualTo("MERCADO EXTRA 123");

        // O PDF gravado já está sem senha.
        byte[] gravado = jdbcTemplate.queryForObject(
                "select pdf from fatura_importacao where id = ?", byte[].class,
                UUID.fromString(importacao.at("/id").asText()));
        try (PDDocument documento = Loader.loadPDF(gravado)) {
            assertThat(documento.isEncrypted()).isFalse();
        }

        JsonNode parcelada = preSaida(importacao, "LOJA X PARC 03/10");
        assertThat(parcelada.at("/cartaoId").asText()).isEqualTo(ref.adicionalId());
        assertThat(parcelada.at("/subcategoriaId").asText()).isEqualTo(ref.subcategoriaId());
        assertThat(parcelada.at("/primeiroVencimento").asText()).isEqualTo(vencimento.minusMonths(2).toString());

        String aprovada = mockMvc.perform(post("/api/pre-saidas/" + parcelada.at("/id").asText() + "/aprovar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APROVADA"))
                .andReturn().getResponse().getContentAsString();
        String saidaId = objectMapper.readTree(aprovada).at("/data/saidaId").asText();
        mockMvc.perform(get("/api/saidas/" + saidaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parcelas.length()").value(10))
                .andExpect(jsonPath("$.data.descricaoFatura").value("LOJA X PARC 03/10"))
                .andExpect(jsonPath("$.data.parcelas[0].vencimento").value(vencimento.minusMonths(2).toString()))
                .andExpect(jsonPath("$.data.parcelas[2].vencimento").value(vencimento.toString()));

        // Sem cartão (final desconhecido) e sem categoria, a pré-saída não pode ser aprovada.
        JsonNode semCartao = preSaida(importacao, "UBER TRIP");
        mockMvc.perform(post("/api/pre-saidas/" + semCartao.at("/id").asText() + "/aprovar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // Reprocessar não duplica: a parcelada agora casa com a saída aprovada.
        JsonNode depois = reprocessar(token, importacao.at("/id").asText());
        assertThat(depois.at("/resumo/conciliados")).hasSize(2);
        assertThat(depois.at("/resumo/preSaidasCriadas").asInt()).isEqualTo(1);
        long pendentes = jdbcTemplate.queryForObject(
                "select count(*) from pre_saida where importacao_id = ? and status = 'PENDENTE'", Long.class,
                UUID.fromString(importacao.at("/id").asText()));
        assertThat(pendentes).isEqualTo(1);
    }

    @Test
    void deveEditarEAprovarPreSaidaSemCartaoReconhecido() throws Exception {
        String token = registrar("importa-editar");
        Referencias ref = criarReferencias(token);
        responderIa(jsonFatura(ref,
                item("1111", "PADARIA", "12.00", 1, 1, "COMPRA", false),
                item("9999", "UBER TRIP", "20.00", 1, 1, "COMPRA", false)));
        JsonNode importacao = importar(token, pdf(null), null);
        JsonNode uber = preSaida(importacao, "UBER TRIP");

        mockMvc.perform(put("/api/pre-saidas/" + uber.at("/id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PreSaidaRequest("Uber", UUID.fromString(ref.principalId()),
                                UUID.fromString(ref.categoriaId()), UUID.fromString(ref.subcategoriaId()),
                                new BigDecimal("20.00"), 2, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primeiroVencimento").value(vencimento.minusMonths(1).toString()));

        String lote = mockMvc.perform(post("/api/pre-saidas/aprovar-lote")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("ids", List.of(
                                uber.at("/id").asText(), preSaida(importacao, "PADARIA").at("/id").asText())))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resultado = objectMapper.readTree(lote).at("/data");
        assertThat(resultado.at("/aprovadas")).hasSize(1);
        assertThat(resultado.at("/falhas")).hasSize(1);
        assertThat(resultado.at("/aprovadas/0/descricao").asText()).isEqualTo("Uber");
    }

    @Test
    void deveRecusarSenhaIncorretaSemGravarImportacao() throws Exception {
        String token = registrar("importa-senha");
        criarReferencias(token);
        responderIa("{}");

        mockMvc.perform(post("/api/faturas/importacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaturaImportacaoRequest(
                                "fatura.pdf", Base64.getEncoder().encodeToString(pdf(SENHA_PDF)), "errada"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/faturas/importacoes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void deveRecusarArquivoQueNaoEPdf() throws Exception {
        String token = registrar("importa-naopdf");
        criarReferencias(token);

        mockMvc.perform(post("/api/faturas/importacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaturaImportacaoRequest(
                                "fatura.pdf", Base64.getEncoder().encodeToString("texto".getBytes()), null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRegistrarErroQuandoIaDevolveJsonInvalidoEPermitirReprocessar() throws Exception {
        String token = registrar("importa-erro");
        Referencias ref = criarReferencias(token);
        responderIa("Desculpe, não consegui ler a fatura.");

        JsonNode importacao = importar(token, pdf(null), null);
        assertThat(importacao.at("/status").asText()).isEqualTo("ERRO");
        assertThat(importacao.at("/erro").asText()).isNotBlank();

        responderIa(jsonFatura(ref, item("1111", "PADARIA", "12.00", 1, 1, "COMPRA", false)));
        JsonNode reprocessada = reprocessar(token, importacao.at("/id").asText());
        assertThat(reprocessada.at("/status").asText()).isEqualTo("CONCLUIDA");
        assertThat(reprocessada.at("/preSaidas")).hasSize(1);
    }

    @Test
    void deveResponderLogoEProcessarEmSegundoPlano() throws Exception {
        String token = registrar("importa-async");
        Referencias ref = criarReferencias(token);
        CountDownLatch liberarIa = new CountDownLatch(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelFactory.obter(any(ProvedorIa.class))).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocacao -> {
            liberarIa.await(10, TimeUnit.SECONDS);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(jsonFatura(ref, item("1111", "PADARIA", "12.00", 1, 1, "COMPRA", false))))
                    .build();
        });

        String body = mockMvc.perform(post("/api/faturas/importacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaturaImportacaoRequest(
                                "fatura.pdf", Base64.getEncoder().encodeToString(pdf(null)), null))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PROCESSANDO"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).at("/data/id").asText();

        mockMvc.perform(get("/api/faturas/importacoes/" + id + "/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSANDO"));
        mockMvc.perform(post("/api/faturas/importacoes/" + id + "/reprocessar").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/faturas/importacoes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        liberarIa.countDown();
        JsonNode concluida = aguardar(token, id);
        assertThat(concluida.at("/status").asText()).isEqualTo("CONCLUIDA");
        assertThat(concluida.at("/preSaidas")).hasSize(1);
        mockMvc.perform(get("/api/faturas/importacoes/" + id + "/status").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.data.preSaidasPendentes").value(1));
    }

    @Test
    void deveMarcarComoErroAsImportacoesInterrompidasPeloReinicio() throws Exception {
        String token = registrar("importa-reinicio");
        Referencias ref = criarReferencias(token);
        responderIa(jsonFatura(ref, item("1111", "PADARIA", "12.00", 1, 1, "COMPRA", false)));
        String id = importar(token, pdf(null), null).at("/id").asText();
        jdbcTemplate.update("update fatura_importacao set status = 'PROCESSANDO' where id = ?", UUID.fromString(id));

        recuperacao.marcarInterrompidas();

        mockMvc.perform(get("/api/faturas/importacoes/" + id + "/status").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("ERRO"))
                .andExpect(jsonPath("$.data.erro").value(RecuperacaoImportacoesInterrompidas.MENSAGEM));
    }

    @Test
    void naoDeveRecriarPreSaidaRejeitadaAoImportarDeNovo() throws Exception {
        String token = registrar("importa-rejeita");
        Referencias ref = criarReferencias(token);
        responderIa(jsonFatura(ref, item("1111", "PADARIA", "12.00", 1, 1, "COMPRA", false)));
        byte[] arquivo = pdf(null);
        JsonNode primeira = importar(token, arquivo, null);

        mockMvc.perform(post("/api/pre-saidas/" + preSaida(primeira, "PADARIA").at("/id").asText() + "/rejeitar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJEITADA"));

        JsonNode segunda = importar(token, arquivo, null);
        assertThat(segunda.at("/preSaidas")).isEmpty();
        assertThat(segunda.at("/resumo/ignorados/0/motivo").asText()).contains("rejeitada");
        assertThat(segunda.at("/resumo/avisos").toString()).contains("já foi importado");
    }

    @Test
    void naoDeveExporImportacaoNemPreSaidaDeOutroUsuario() throws Exception {
        String token = registrar("importa-dono");
        Referencias ref = criarReferencias(token);
        responderIa(jsonFatura(ref, item("1111", "PADARIA", "12.00", 1, 1, "COMPRA", false)));
        JsonNode importacao = importar(token, pdf(null), null);

        String intruso = registrar("importa-intruso");
        mockMvc.perform(get("/api/faturas/importacoes/" + importacao.at("/id").asText())
                        .header("Authorization", "Bearer " + intruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/faturas/importacoes/" + importacao.at("/id").asText() + "/pdf")
                        .header("Authorization", "Bearer " + intruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/pre-saidas/" + preSaida(importacao, "PADARIA").at("/id").asText() + "/aprovar")
                        .header("Authorization", "Bearer " + intruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pre-saidas").header("Authorization", "Bearer " + intruso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/faturas/importacoes/" + importacao.at("/id").asText() + "/pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
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
                new CategoriaRequest("Compras importadas", TipoCategoria.DESPESA, true));
        String subcategoriaId = criar(token, "/api/categorias/" + categoriaId + "/subcategorias",
                new SubcategoriaCreateRequest("Lojas", true));
        String principalId = criar(token, "/api/cartoes",
                new CartaoRequest("Principal", "Banco", Bandeira.VISA, "1111", null));
        String adicionalId = criar(token, "/api/cartoes", new CartaoRequest(
                "Adicional", "Banco", Bandeira.VISA, "2222", null, TipoCartao.ADICIONAL, true,
                UUID.fromString(principalId)));
        criar(token, "/api/ia/provedores", new ProvedorIaRequest("Teste", TipoProvedorIa.OPENAI, "gpt-4o-mini",
                null, "sk-teste-1234", null, null, 60, true, true));
        return new Referencias(categoriaId, subcategoriaId, principalId, adicionalId);
    }

    private SaidaRequest compraAVista(Referencias ref, String descricao, String valor) {
        return new SaidaRequest(descricao, null, new BigDecimal(valor), 1, FormaPagamento.CARTAO_CREDITO, null,
                vencimento, UUID.fromString(ref.principalId()), UUID.fromString(ref.categoriaId()),
                UUID.fromString(ref.subcategoriaId()), ModoResponsabilidade.USUARIO, null, List.of());
    }

    private void responderIa(String resposta) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelFactory.obter(any(ProvedorIa.class))).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(resposta))
                .modelName("gpt-teste")
                .build());
    }

    private String jsonFatura(Referencias ref, String... itens) {
        return ("```json\n{\"vencimento\": \"%s\", \"total\": 482.00, \"cartoes\": [{\"ultimosDigitos\": \"1111\"}],"
                + " \"itens\": [%s]}\n```").formatted(vencimento, String.join(",", itens))
                .replace("SUB_ID", ref.subcategoriaId());
    }

    private static String item(String digitos, String descricao, String valor, int parcela, int total, String tipo,
            boolean comSubcategoria) {
        return ("{\"ultimosDigitos\": %s, \"data\": null, \"descricao\": \"%s\", \"valor\": %s, \"parcelaAtual\": %d,"
                + " \"totalParcelas\": %d, \"tipo\": \"%s\", \"categoriaId\": null, \"subcategoriaId\": %s}")
                .formatted(digitos == null ? "null" : "\"" + digitos + "\"", descricao, valor, parcela, total, tipo,
                        comSubcategoria ? "\"SUB_ID\"" : "null");
    }

    private JsonNode importar(String token, byte[] pdf, String senha) throws Exception {
        String body = mockMvc.perform(post("/api/faturas/importacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaturaImportacaoRequest(
                                "fatura.pdf", "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdf),
                                senha))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return aguardar(token, objectMapper.readTree(body).at("/data/id").asText());
    }

    private JsonNode reprocessar(String token, String id) throws Exception {
        mockMvc.perform(post("/api/faturas/importacoes/" + id + "/reprocessar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());
        return aguardar(token, id);
    }

    /** Consulta o status como o frontend faz, até o processamento em segundo plano terminar. */
    private JsonNode aguardar(String token, String id) throws Exception {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            String status = mockMvc.perform(get("/api/faturas/importacoes/" + id + "/status")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            if (!"PROCESSANDO".equals(objectMapper.readTree(status).at("/data/status").asText())) {
                String detalhe = mockMvc.perform(get("/api/faturas/importacoes/" + id)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(detalhe).at("/data");
            }
            Thread.sleep(50);
        }
        throw new AssertionError("A importação " + id + " não terminou de processar");
    }

    private static JsonNode preSaida(JsonNode importacao, String descricao) {
        for (JsonNode preSaida : importacao.at("/preSaidas")) {
            if (preSaida.at("/descricaoFatura").asText().equals(descricao)) {
                return preSaida;
            }
        }
        throw new AssertionError("Pré-saída não encontrada: " + descricao);
    }

    private static byte[] pdf(String senha) throws Exception {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                conteudo.newLineAtOffset(50, 700);
                conteudo.showText("FATURA CARTAO FINAL 1111 - LOJA X PARC 03/10 100,00");
                conteudo.endText();
            }
            if (senha != null) {
                StandardProtectionPolicy politica = new StandardProtectionPolicy(senha + "-dono", senha, new AccessPermission());
                politica.setEncryptionKeyLength(128);
                documento.protect(politica);
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        }
    }
}
