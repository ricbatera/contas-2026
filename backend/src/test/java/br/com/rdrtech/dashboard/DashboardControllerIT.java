package br.com.rdrtech.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveRetornarResumoDaCompetenciaEValidarAutenticacaoEParametros() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Ana", "Silva", "dashboard-" + System.nanoTime() + "@example.com", "Senha123!", null);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).at("/data/accessToken").asText();

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "10")
                        .param("ano", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mes").value(10))
                .andExpect(jsonPath("$.data.ano").value(2026))
                .andExpect(jsonPath("$.data.gastosLancados").value(0))
                .andExpect(jsonPath("$.data.bucketsReservados").value(0))
                .andExpect(jsonPath("$.data.contasFixasMesAnterior").value(0))
                .andExpect(jsonPath("$.data.totalPrevisto").value(0));

        mockMvc.perform(get("/api/dashboard/saidas")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "10")
                        .param("ano", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/dashboard/buckets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/dashboard/contas-fixas")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "10")
                        .param("ano", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/dashboard/projecao")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "10")
                        .param("ano", "2026")
                        .param("quantidade", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].mes").value(10))
                .andExpect(jsonPath("$.data[0].ano").value(2026))
                .andExpect(jsonPath("$.data[0].totalPrevisto").value(0))
                .andExpect(jsonPath("$.data[2].mes").value(12));
        mockMvc.perform(get("/api/dashboard/projecao")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "10")
                        .param("ano", "2026")
                        .param("quantidade", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(24))
                .andExpect(jsonPath("$.data[23].mes").value(9))
                .andExpect(jsonPath("$.data[23].ano").value(2028));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "13")
                        .param("ano", "2026"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dashboard/projecao")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "10")
                        .param("ano", "2026")
                        .param("quantidade", "25"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dashboard/projecao")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "10")
                        .param("ano", "2026")
                        .param("quantidade", "2"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dashboard").param("mes", "10").param("ano", "2026"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/buckets"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/projecao")
                        .param("mes", "10")
                        .param("ano", "2026")
                        .param("quantidade", "12"))
                .andExpect(status().isUnauthorized());
    }
}
