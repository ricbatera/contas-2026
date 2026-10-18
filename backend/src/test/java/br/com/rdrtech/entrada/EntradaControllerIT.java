package br.com.rdrtech.entrada;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.contabancaria.TipoConta;
import br.com.rdrtech.contabancaria.dto.ContaBancariaRequest;
import br.com.rdrtech.entrada.dto.EntradaRequest;
import br.com.rdrtech.entrada.dto.ReceberOcorrenciaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EntradaControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

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

    private Referencias criarReferencias(String token) throws Exception {
        String categoriaId = postAndReadId(
                token, "/api/categorias", new CategoriaRequest("Renda extra", TipoCategoria.RECEITA, true));
        String subcategoriaId = postAndReadId(
                token,
                "/api/categorias/" + categoriaId + "/subcategorias",
                new SubcategoriaCreateRequest("Consultoria", true));
        String contaId = postAndReadId(
                token,
                "/api/contas-bancarias",
                new ContaBancariaRequest(
                        "Conta salário", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true));
        return new Referencias(categoriaId, subcategoriaId, contaId);
    }

    private EntradaRequest request(
            Referencias referencias, String valor, boolean recorrente, LocalDate inicio, LocalDate fim) {
        return new EntradaRequest(
                "Salário",
                new BigDecimal(valor),
                recorrente,
                inicio,
                fim,
                UUID.fromString(referencias.categoriaId()),
                UUID.fromString(referencias.subcategoriaId()),
                UUID.fromString(referencias.contaId()));
    }

    private JsonNode ocorrencias(String token, YearMonth competencia) throws Exception {
        String response = mockMvc.perform(get("/api/entradas/ocorrencias")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", String.valueOf(competencia.getMonthValue()))
                        .param("ano", String.valueOf(competencia.getYear())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data");
    }

    private void receber(String token, String ocorrenciaId, String valor, LocalDate data) throws Exception {
        mockMvc.perform(post("/api/entradas/ocorrencias/" + ocorrenciaId + "/receber")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReceberOcorrenciaRequest(new BigDecimal(valor), data))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.data.valorRecebido").value(Double.parseDouble(valor)));
    }

    @Test
    void entradaAvulsaDeveTerUmaOcorrenciaEPermitirReceberCancelarEReabrir() throws Exception {
        String token = registrar("entrada-avulsa");
        Referencias referencias = criarReferencias(token);
        LocalDate data = LocalDate.of(2026, 3, 10);

        String id = postAndReadId(token, "/api/entradas", request(referencias, "800.00", false, data, null));

        mockMvc.perform(get("/api/entradas/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recorrente").value(false))
                .andExpect(jsonPath("$.data.contaBancariaNome").value("Conta salário"));
        assertThat(ocorrencias(token, YearMonth.of(2026, 4))).isEmpty();
        JsonNode lista = ocorrencias(token, YearMonth.of(2026, 3));
        assertThat(lista).hasSize(1);
        String ocorrenciaId = lista.get(0).get("id").asText();
        assertThat(lista.get(0).get("status").asText()).isEqualTo("PREVISTA");

        receber(token, ocorrenciaId, "750.00", LocalDate.of(2026, 3, 12));
        mockMvc.perform(post("/api/entradas/ocorrencias/" + ocorrenciaId + "/cancelar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/entradas/ocorrencias/" + ocorrenciaId + "/reabrir")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREVISTA"))
                .andExpect(jsonPath("$.data.valorRecebido").doesNotExist());
        mockMvc.perform(post("/api/entradas/ocorrencias/" + ocorrenciaId + "/cancelar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADA"));
        mockMvc.perform(post("/api/entradas/ocorrencias/" + ocorrenciaId + "/receber")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReceberOcorrenciaRequest(new BigDecimal("10.00"), data))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/entradas/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/entradas/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        assertThat(ocorrencias(token, YearMonth.of(2026, 3))).isEmpty();
    }

    @Test
    void entradaRecorrenteDeveGerarOcorrenciaMensalAjustandoODia31() throws Exception {
        String token = registrar("entrada-recorrente");
        Referencias referencias = criarReferencias(token);

        postAndReadId(token, "/api/entradas",
                request(referencias, "5000.00", true, LocalDate.of(2026, 1, 31), null));

        JsonNode fevereiro = ocorrencias(token, YearMonth.of(2026, 2));
        assertThat(fevereiro).hasSize(1);
        assertThat(fevereiro.get(0).get("dataPrevista").asText())
                .isEqualTo("2026-02-28");
        assertThat(ocorrencias(token, YearMonth.of(2026, 3)).get(0)
                .get("dataPrevista").asText()).isEqualTo("2026-03-31");
        assertThat(ocorrencias(token, YearMonth.of(2025, 12))).isEmpty();

        // Além do que o cadastro gerou, o horizonte é estendido sob demanda até o limite máximo.
        YearMonth distante = YearMonth.now().plusMonths(30);
        assertThat(ocorrencias(token, distante)).hasSize(1);
        assertThat(ocorrencias(token, distante.minusMonths(1))).hasSize(1);
        assertThat(ocorrencias(token, YearMonth.now().plusMonths(100))).isEmpty();
    }

    @Test
    void edicaoDevePreservarRecebidasERegerarPrevistas() throws Exception {
        String token = registrar("entrada-edicao");
        Referencias referencias = criarReferencias(token);
        String id = postAndReadId(token, "/api/entradas",
                request(referencias, "5000.00", true, LocalDate.of(2026, 1, 5), null));
        String janeiroId = ocorrencias(token, YearMonth.of(2026, 1)).get(0).get("id").asText();
        receber(token, janeiroId, "4900.00", LocalDate.of(2026, 1, 5));

        mockMvc.perform(put("/api/entradas/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(referencias, "5500.00", true, LocalDate.of(2026, 1, 5),
                                        LocalDate.of(2026, 6, 30)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valor").value(5500.00))
                .andExpect(jsonPath("$.data.dataFinal").value("2026-06-30"));

        JsonNode janeiro = ocorrencias(token, YearMonth.of(2026, 1)).get(0);
        assertThat(janeiro.get("id").asText()).isEqualTo(janeiroId);
        assertThat(janeiro.get("status").asText()).isEqualTo("RECEBIDA");
        assertThat(janeiro.get("valorPrevisto").decimalValue())
                .isEqualByComparingTo("5000.00");
        assertThat(ocorrencias(token, YearMonth.of(2026, 6)).get(0)
                .get("valorPrevisto").decimalValue()).isEqualByComparingTo("5500.00");
        assertThat(ocorrencias(token, YearMonth.of(2026, 7))).isEmpty();

        // A recebida de janeiro ficaria fora do período: a alteração é recusada.
        mockMvc.perform(put("/api/entradas/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(referencias, "5500.00", true, LocalDate.of(2026, 2, 5), null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarCategoriaDeDespesaEDataFinalEmAvulsa() throws Exception {
        String token = registrar("entrada-invalida");
        Referencias referencias = criarReferencias(token);
        String despesaId = postAndReadId(
                token, "/api/categorias", new CategoriaRequest("Casa", TipoCategoria.DESPESA, true));
        String subDespesaId = postAndReadId(
                token, "/api/categorias/" + despesaId + "/subcategorias", new SubcategoriaCreateRequest("Luz", true));

        mockMvc.perform(post("/api/entradas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EntradaRequest(
                                "Errada", new BigDecimal("10.00"), false, LocalDate.of(2026, 1, 1), null,
                                UUID.fromString(despesaId), UUID.fromString(subDespesaId), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selecione uma categoria de receita."));
        mockMvc.perform(post("/api/entradas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(
                                referencias, "10.00", false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void naoDevePermitirExcluirCategoriaOuContaVinculadas() throws Exception {
        String token = registrar("entrada-vinculos");
        Referencias referencias = criarReferencias(token);
        postAndReadId(token, "/api/entradas",
                request(referencias, "100.00", false, LocalDate.of(2026, 1, 1), null));

        mockMvc.perform(delete("/api/categorias/" + referencias.categoriaId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/contas-bancarias/" + referencias.contaId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void entradasDevemSerIsoladasPorUsuario() throws Exception {
        String dono = registrar("entrada-dono");
        String outro = registrar("entrada-outro");
        Referencias referencias = criarReferencias(dono);
        String id = postAndReadId(dono, "/api/entradas",
                request(referencias, "100.00", true, LocalDate.of(2026, 1, 1), null));
        String ocorrenciaId = ocorrencias(dono, YearMonth.of(2026, 1)).get(0).get("id").asText();

        mockMvc.perform(get("/api/entradas/" + id).header("Authorization", "Bearer " + outro))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/entradas/ocorrencias/" + ocorrenciaId + "/cancelar")
                        .header("Authorization", "Bearer " + outro))
                .andExpect(status().isNotFound());
        assertThat(ocorrencias(outro, YearMonth.of(2026, 1))).isEmpty();
    }

    @Test
    void dashboardDeveSomarReceitasECalcularSaldo() throws Exception {
        String token = registrar("entrada-dashboard");
        Referencias referencias = criarReferencias(token);
        postAndReadId(token, "/api/entradas",
                request(referencias, "3000.00", true, LocalDate.of(2026, 1, 5), null));
        String avulsaId = postAndReadId(token, "/api/entradas",
                request(referencias, "500.00", false, LocalDate.of(2026, 2, 20), null));
        String ocorrenciaAvulsaId = null;
        for (JsonNode ocorrencia : ocorrencias(token, YearMonth.of(2026, 2))) {
            if (ocorrencia.get("entradaId").asText().equals(avulsaId)) {
                ocorrenciaAvulsaId = ocorrencia.get("id").asText();
            }
        }
        receber(token, ocorrenciaAvulsaId, "450.00", LocalDate.of(2026, 2, 20));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "2")
                        .param("ano", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receitasPrevistas").value(3450.00))
                .andExpect(jsonPath("$.data.receitasRecebidas").value(450.00))
                .andExpect(jsonPath("$.data.saldoPrevisto").value(3450.00));
        mockMvc.perform(get("/api/dashboard/projecao")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "1")
                        .param("ano", "2026")
                        .param("quantidade", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].receitasPrevistas").value(3000.00))
                .andExpect(jsonPath("$.data[1].receitasPrevistas").value(3450.00))
                .andExpect(jsonPath("$.data[2].receitasPrevistas").value(3000.00));
        mockMvc.perform(get("/api/dashboard/entradas")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "2")
                        .param("ano", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private record Referencias(String categoriaId, String subcategoriaId, String contaId) {
    }
}
