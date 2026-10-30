package br.com.rdrtech.saida;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.cartao.Bandeira;
import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.pessoa.dto.PessoaRequest;
import br.com.rdrtech.saida.dto.GerarContaFixaItemRequest;
import br.com.rdrtech.saida.dto.GerarContasFixasRequest;
import br.com.rdrtech.saida.dto.SaidaParteRequest;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ContasFixasIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void previaDeveUsarUltimaOcorrenciaDaSerieEIgnorarCreditoENaoFixas() throws Exception {
        String token = registrar("conta-fixa-previa");
        Referencias referencias = criarReferencias(token);
        String internet = criarSaida(token, referencias, "Internet", "100.00", LocalDate.of(2026, 8, 31), true);
        criarSaida(token, referencias, "Mercado", "300.00", LocalDate.of(2026, 10, 5), false);
        String cartaoId = postAndReadId(token, "/api/cartoes",
                new CartaoRequest("Principal", "Banco", Bandeira.VISA, "1111", null));
        postAndReadId(token, "/api/saidas", new SaidaRequest(
                "Streaming", null, new BigDecimal("40.00"), 1, FormaPagamento.CARTAO_CREDITO,
                null, LocalDate.of(2026, 10, 15), UUID.fromString(cartaoId),
                referencias.categoriaId(), referencias.subcategoriaId(),
                ModoResponsabilidade.USUARIO, null, List.of(), true));

        previa(token, 9, 2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].saidaOrigemId").value(internet));

        String geradaEmSetembro = gerar(token, 9, 2026, internet, "110.00", LocalDate.of(2026, 9, 30))
                .andExpect(status().isCreated())
                .andReturnId("/data/criadas/0/id");

        // Outubro ficou sem a internet: a fonte é a última ocorrência da série, mesmo com mês pulado.
        previa(token, 11, 2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].serieId").value(internet))
                .andExpect(jsonPath("$.data[0].saidaOrigemId").value(geradaEmSetembro))
                .andExpect(jsonPath("$.data[0].descricao").value("Internet"))
                .andExpect(jsonPath("$.data[0].valorPorParcela").value(110.00))
                .andExpect(jsonPath("$.data[0].dataSugerida").value("2026-11-30"))
                .andExpect(jsonPath("$.data[0].responsaveis[0]").value("Ana Silva"))
                .andExpect(jsonPath("$.data[0].aviso").doesNotExist());
    }

    @Test
    void gerarDeveCriarSaidaNaSerieSemDuplicarEDescontarDaPrevisaoDoDashboard() throws Exception {
        String token = registrar("conta-fixa-gerar");
        Referencias referencias = criarReferencias(token);
        String luz = postAndReadId(token, "/api/saidas", new SaidaRequest(
                "Luz", null, new BigDecimal("200.00"), 1, FormaPagamento.BOLETO,
                LocalDate.of(2026, 10, 5), null, null,
                referencias.categoriaId(), referencias.subcategoriaId(),
                ModoResponsabilidade.DIVIDIR, null,
                List.of(new SaidaParteRequest(referencias.pessoaId(), new BigDecimal("50.00"))), true));

        mockMvc.perform(get("/api/dashboard").param("mes", "11").param("ano", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.contasFixas").value(150.00))
                .andExpect(jsonPath("$.data.contasFixasPrevistas").value(150.00));
        mockMvc.perform(get("/api/dashboard/contas-fixas").param("mes", "11").param("ano", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].situacao").value("PREVISTA"))
                .andExpect(jsonPath("$.data[0].vencimento").value("2026-11-05"))
                .andExpect(jsonPath("$.data[0].valor").value(150.00));

        gerar(token, 11, 2026, luz, "300.00", LocalDate.of(2026, 11, 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ignoradas").value(0))
                .andExpect(jsonPath("$.data.criadas", hasSize(1)))
                .andExpect(jsonPath("$.data.criadas[0].descricao").value("Luz"))
                .andExpect(jsonPath("$.data.criadas[0].contaFixa").value(true))
                .andExpect(jsonPath("$.data.criadas[0].serieContaFixaId").value(luz))
                .andExpect(jsonPath("$.data.criadas[0].formaPagamento").value("BOLETO"))
                .andExpect(jsonPath("$.data.criadas[0].quantidadeParcelas").value(1))
                .andExpect(jsonPath("$.data.criadas[0].parcelas[0].vencimento").value("2026-11-05"))
                .andExpect(jsonPath("$.data.criadas[0].responsabilidades[?(@.pessoaId != null)].valorPorParcela")
                        .value(75.00))
                .andExpect(jsonPath("$.data.criadas[0].responsabilidades[?(@.usuarioId != null)].valorPorParcela")
                        .value(225.00));

        gerar(token, 11, 2026, luz, "300.00", LocalDate.of(2026, 11, 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ignoradas").value(1))
                .andExpect(jsonPath("$.data.criadas", hasSize(0)));
        previa(token, 11, 2026)
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/saidas").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/dashboard").param("mes", "11").param("ano", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.gastosLancados").value(0))
                .andExpect(jsonPath("$.data.contasFixas").value(225.00))
                .andExpect(jsonPath("$.data.contasFixasPrevistas").value(0));
        mockMvc.perform(get("/api/dashboard/contas-fixas").param("mes", "11").param("ano", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].situacao").value("GERADA"))
                .andExpect(jsonPath("$.data[0].valor").value(225.00));
        mockMvc.perform(get("/api/dashboard/saidas").param("mes", "11").param("ano", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/dashboard/projecao").param("mes", "11").param("ano", "2026")
                        .param("quantidade", "3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].contasFixas").value(225.00))
                .andExpect(jsonPath("$.data[0].contasFixasPrevistas").value(0))
                .andExpect(jsonPath("$.data[1].contasFixas").value(225.00))
                .andExpect(jsonPath("$.data[1].contasFixasPrevistas").value(225.00));
    }

    @Test
    void previaDeveIgnorarContaFixaLancadaAMaoNoMes() throws Exception {
        String token = registrar("conta-fixa-manual");
        Referencias referencias = criarReferencias(token);
        criarSaida(token, referencias, "Água", "80.00", LocalDate.of(2026, 10, 10), true);
        criarSaida(token, referencias, " água ", "85.00", LocalDate.of(2026, 11, 12), true);

        previa(token, 11, 2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void gerarDeveRecusarDataForaDoMesESerieDeOutroUsuario() throws Exception {
        String token = registrar("conta-fixa-invalida");
        Referencias referencias = criarReferencias(token);
        String gas = criarSaida(token, referencias, "Gás", "60.00", LocalDate.of(2026, 10, 20), true);

        gerar(token, 11, 2026, gas, "60.00", LocalDate.of(2026, 12, 1))
                .andExpect(status().isBadRequest());

        String outroToken = registrar("conta-fixa-outro-tenant");
        previa(outroToken, 11, 2026)
                .andExpect(jsonPath("$.data", hasSize(0)));
        gerar(outroToken, 11, 2026, gas, "60.00", LocalDate.of(2026, 11, 20))
                .andExpect(status().isNotFound());
        previa(token, 11, 2026)
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    private ResultActions previa(String token, int mes, int ano) throws Exception {
        return mockMvc.perform(get("/api/saidas/contas-fixas/previa")
                .param("mes", String.valueOf(mes))
                .param("ano", String.valueOf(ano))
                .header("Authorization", "Bearer " + token));
    }

    private Resultado gerar(String token, int mes, int ano, String serieId, String valor, LocalDate data)
            throws Exception {
        GerarContasFixasRequest request = new GerarContasFixasRequest(mes, ano, List.of(
                new GerarContaFixaItemRequest(UUID.fromString(serieId), new BigDecimal(valor), data)));
        return new Resultado(mockMvc.perform(post("/api/saidas/contas-fixas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))));
    }

    private String criarSaida(
            String token, Referencias referencias, String descricao, String valor, LocalDate data, boolean contaFixa)
            throws Exception {
        return postAndReadId(token, "/api/saidas", new SaidaRequest(
                descricao, null, new BigDecimal(valor), 1, FormaPagamento.PIX, data, null, null,
                referencias.categoriaId(), referencias.subcategoriaId(),
                ModoResponsabilidade.USUARIO, null, List.of(), contaFixa));
    }

    private Referencias criarReferencias(String token) throws Exception {
        String categoriaId = postAndReadId(token, "/api/categorias",
                new CategoriaRequest("Serviços da casa", TipoCategoria.DESPESA, true));
        String subcategoriaId = postAndReadId(token, "/api/categorias/" + categoriaId + "/subcategorias",
                new SubcategoriaCreateRequest("Contas de consumo", true, null));
        String pessoaId = postAndReadId(token, "/api/pessoas", new PessoaRequest("Bruno", true));
        return new Referencias(
                UUID.fromString(categoriaId), UUID.fromString(subcategoriaId), UUID.fromString(pessoaId));
    }

    private String registrar(String prefix) throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Ana", "Silva", prefix + "-" + System.nanoTime() + "@example.com", "Senha123!", null);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private String postAndReadId(String token, String path, Object request) throws Exception {
        String response = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asText();
    }

    private record Referencias(UUID categoriaId, UUID subcategoriaId, UUID pessoaId) {
    }

    /** Envolve o {@link ResultActions} para também ler um campo da resposta depois das verificações. */
    private final class Resultado {

        private final ResultActions actions;

        private Resultado(ResultActions actions) {
            this.actions = actions;
        }

        Resultado andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        String andReturnId(String pointer) throws Exception {
            String response = actions.andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).at(pointer).asText();
        }
    }
}
