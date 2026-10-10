package br.com.rdrtech.pessoa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.pessoa.dto.PessoaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PessoaControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registrarERetornarToken(String prefixoEmail) throws Exception {
        String email = prefixoEmail + "-" + System.currentTimeMillis() + "-" + System.nanoTime() + "@example.com";
        RegisterRequest request = new RegisterRequest("Usuario", "Teste", email, "Senha123!", null);
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private String criarPessoa(String token, String nome) throws Exception {
        PessoaRequest request = new PessoaRequest(nome, null);
        String body = mockMvc.perform(post("/api/pessoas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    @Test
    void fluxoCompleto_deveCriarListarBuscarAtualizarEExcluirPessoa() throws Exception {
        String token = registrarERetornarToken("pessoa-crud");
        String id = criarPessoa(token, "Maria Silva");

        mockMvc.perform(get("/api/pessoas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nome").value("Maria Silva"))
                .andExpect(jsonPath("$.data[0].ativo").value(true));

        mockMvc.perform(get("/api/pessoas/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        PessoaRequest update = new PessoaRequest("Maria Atualizada", false);
        mockMvc.perform(put("/api/pessoas/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Maria Atualizada"))
                .andExpect(jsonPath("$.data.ativo").value(false));

        mockMvc.perform(delete("/api/pessoas/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/pessoas/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_deveIsolarPessoasPorTenant() throws Exception {
        String tokenA = registrarERetornarToken("pessoa-tenant-a");
        String tokenB = registrarERetornarToken("pessoa-tenant-b");
        criarPessoa(tokenA, "Somente A");
        criarPessoa(tokenB, "Somente B");

        mockMvc.perform(get("/api/pessoas").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nome").value("Somente A"));

        mockMvc.perform(get("/api/pessoas").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nome").value("Somente B"));
    }

    @Test
    void buscarPorId_deveRetornar404_quandoPessoaPertenceAOutroTenant() throws Exception {
        String tokenA = registrarERetornarToken("pessoa-dona");
        String tokenB = registrarERetornarToken("pessoa-outra");
        String id = criarPessoa(tokenA, "Privada");

        mockMvc.perform(get("/api/pessoas/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_deveRetornar400_quandoNomeAusente() throws Exception {
        String token = registrarERetornarToken("pessoa-invalida");
        mockMvc.perform(post("/api/pessoas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \",\"ativo\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operacoesDevemExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/api/pessoas")).andExpect(status().isUnauthorized());
    }
}
