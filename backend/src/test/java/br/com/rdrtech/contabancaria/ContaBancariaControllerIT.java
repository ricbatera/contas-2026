package br.com.rdrtech.contabancaria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.contabancaria.dto.ContaBancariaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ContaBancariaControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

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

    private String criarContaERetornarId(String token, ContaBancariaRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/contas-bancarias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    @Test
    void criar_deveRetornar201EComContaCriada_quandoDadosValidos() throws Exception {
        String token = registrarERetornarToken("cria");
        ContaBancariaRequest request = new ContaBancariaRequest(
                "Conta Corrente Itaú", "Itaú", TipoConta.CORRENTE, "1234", "56789-0",
                BigDecimal.valueOf(1000), "#EC7000", true);

        mockMvc.perform(post("/api/contas-bancarias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nome").value("Conta Corrente Itaú"))
                .andExpect(jsonPath("$.data.banco").value("Itaú"))
                .andExpect(jsonPath("$.data.tipoConta").value("CORRENTE"))
                .andExpect(jsonPath("$.data.agencia").value("1234"))
                .andExpect(jsonPath("$.data.numeroConta").value("56789-0"))
                .andExpect(jsonPath("$.data.saldoInicial").value(1000))
                .andExpect(jsonPath("$.data.cor").value("#EC7000"))
                .andExpect(jsonPath("$.data.ativo").value(true));
    }

    @Test
    void criar_deveRetornar400_quandoCorInvalida() throws Exception {
        String token = registrarERetornarToken("invalido");
        ContaBancariaRequest request = new ContaBancariaRequest(
                "Conta", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, "azul", true);

        mockMvc.perform(post("/api/contas-bancarias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_deveRetornar409_quandoNomeDuplicadoParaMesmoUsuario() throws Exception {
        String token = registrarERetornarToken("dup");
        ContaBancariaRequest request = new ContaBancariaRequest(
                "Conta Principal", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true);
        criarContaERetornarId(token, request);

        ContaBancariaRequest duplicado = new ContaBancariaRequest(
                "Conta Principal", "Outro Banco", TipoConta.POUPANCA, null, null, BigDecimal.ZERO, null, true);
        mockMvc.perform(post("/api/contas-bancarias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_deveRetornar409_quandoUltrapassa20Contas() throws Exception {
        String token = registrarERetornarToken("limite");
        for (int i = 0; i < 20; i++) {
            ContaBancariaRequest request = new ContaBancariaRequest(
                    "Conta " + i, "Banco", TipoConta.OUTRO, null, null, BigDecimal.ZERO, null, true);
            criarContaERetornarId(token, request);
        }

        ContaBancariaRequest vigesimaPrimeira = new ContaBancariaRequest(
                "Conta 21", "Banco", TipoConta.OUTRO, null, null, BigDecimal.ZERO, null, true);
        mockMvc.perform(post("/api/contas-bancarias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vigesimaPrimeira)))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_deveFuncionarParaDoisUsuarios_quandoNomeIgual() throws Exception {
        String tokenA = registrarERetornarToken("usera");
        String tokenB = registrarERetornarToken("userb");
        ContaBancariaRequest request = new ContaBancariaRequest(
                "Conta Igual", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true);

        mockMvc.perform(post("/api/contas-bancarias")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/contas-bancarias")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void buscarPorId_deveRetornar404_quandoContaPertenceAOutroUsuario() throws Exception {
        String tokenA = registrarERetornarToken("dona");
        String tokenB = registrarERetornarToken("outra");
        String id = criarContaERetornarId(tokenA, new ContaBancariaRequest(
                "Conta", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true));

        mockMvc.perform(get("/api/contas-bancarias/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_deveRetornar404_quandoContaPertenceAOutroUsuario() throws Exception {
        String tokenA = registrarERetornarToken("dona2");
        String tokenB = registrarERetornarToken("outra2");
        String id = criarContaERetornarId(tokenA, new ContaBancariaRequest(
                "Conta", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true));

        ContaBancariaRequest atualizacao = new ContaBancariaRequest(
                "Novo Nome", "Novo Banco", TipoConta.POUPANCA, null, null, BigDecimal.ZERO, null, true);
        mockMvc.perform(put("/api/contas-bancarias/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizacao)))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluir_deveRetornar404_quandoContaPertenceAOutroUsuario() throws Exception {
        String tokenA = registrarERetornarToken("dona3");
        String tokenB = registrarERetornarToken("outra3");
        String id = criarContaERetornarId(tokenA, new ContaBancariaRequest(
                "Conta", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true));

        mockMvc.perform(delete("/api/contas-bancarias/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_deveRetornarApenasContasDoUsuarioAutenticado() throws Exception {
        String tokenA = registrarERetornarToken("lista-a");
        String tokenB = registrarERetornarToken("lista-b");
        criarContaERetornarId(tokenA, new ContaBancariaRequest(
                "Conta A1", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true));
        criarContaERetornarId(tokenA, new ContaBancariaRequest(
                "Conta A2", "Banco", TipoConta.POUPANCA, null, null, BigDecimal.ZERO, null, true));
        criarContaERetornarId(tokenB, new ContaBancariaRequest(
                "Conta B1", "Banco", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true));

        mockMvc.perform(get("/api/contas-bancarias").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/contas-bancarias").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void atualizar_deveAtualizarCamposDaConta_quandoDadosValidos() throws Exception {
        String token = registrarERetornarToken("update");
        String id = criarContaERetornarId(token, new ContaBancariaRequest(
                "Conta Antiga", "Banco Antigo", TipoConta.CORRENTE, null, null, BigDecimal.ZERO, null, true));

        ContaBancariaRequest atualizacao = new ContaBancariaRequest(
                "Conta Nova", "Banco Novo", TipoConta.POUPANCA, "4321", "0-98765",
                BigDecimal.valueOf(250), "#000000", false);
        mockMvc.perform(put("/api/contas-bancarias/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizacao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Conta Nova"))
                .andExpect(jsonPath("$.data.banco").value("Banco Novo"))
                .andExpect(jsonPath("$.data.tipoConta").value("POUPANCA"))
                .andExpect(jsonPath("$.data.agencia").value("4321"))
                .andExpect(jsonPath("$.data.numeroConta").value("0-98765"))
                .andExpect(jsonPath("$.data.saldoInicial").value(250))
                .andExpect(jsonPath("$.data.cor").value("#000000"))
                .andExpect(jsonPath("$.data.ativo").value(false));
    }

    @Test
    void excluir_deveRemoverContaBancaria_eBuscaSubsequenteRetorna404() throws Exception {
        String token = registrarERetornarToken("delete");
        String id = criarContaERetornarId(token, new ContaBancariaRequest(
                "Conta", "Banco", TipoConta.OUTRO, null, null, BigDecimal.ZERO, null, true));

        mockMvc.perform(delete("/api/contas-bancarias/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contas-bancarias/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_deveRetornar401_quandoNaoAutenticado() throws Exception {
        mockMvc.perform(get("/api/contas-bancarias")).andExpect(status().isUnauthorized());
    }
}
