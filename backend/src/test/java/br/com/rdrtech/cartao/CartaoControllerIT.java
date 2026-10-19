package br.com.rdrtech.cartao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.cartao.dto.CartaoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CartaoControllerIT {

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

    private String criarCartaoERetornarId(String token, CartaoRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    @Test
    void criar_deveRetornar201EComCartaoCriado_quandoDadosValidos() throws Exception {
        String token = registrarERetornarToken("cria");
        CartaoRequest request = new CartaoRequest("Nubank Roxinho", "Nubank", Bandeira.MASTERCARD, "1234", "#8A05BE");

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nome").value("Nubank Roxinho"))
                .andExpect(jsonPath("$.data.banco").value("Nubank"))
                .andExpect(jsonPath("$.data.bandeira").value("MASTERCARD"))
                .andExpect(jsonPath("$.data.ultimosDigitos").value("1234"))
                .andExpect(jsonPath("$.data.cor").value("#8A05BE"))
                .andExpect(jsonPath("$.data.tipo").value("PRINCIPAL"))
                .andExpect(jsonPath("$.data.virtual").value(false))
                .andExpect(jsonPath("$.data.cartaoPrincipalId").value(Matchers.nullValue()));
    }

    @Test
    void criar_deveAssociarCartaoAdicionalVirtualAoPrincipal() throws Exception {
        String token = registrarERetornarToken("adicional");
        String principalId = criarCartaoERetornarId(token,
                new CartaoRequest("Principal", "Banco", Bandeira.VISA, "2468", null));
        CartaoRequest adicional = new CartaoRequest(
                "Virtual adicional", "Banco", Bandeira.VISA, "1357", "#123456",
                TipoCartao.ADICIONAL, true, java.util.UUID.fromString(principalId));

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adicional)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tipo").value("ADICIONAL"))
                .andExpect(jsonPath("$.data.virtual").value(true))
                .andExpect(jsonPath("$.data.cartaoPrincipalId").value(principalId));

        mockMvc.perform(get("/api/cartoes/principais").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(principalId));
    }

    @Test
    void criar_deveAceitarDiasDeFechamentoEVencimentoSoNoPrincipal() throws Exception {
        String token = registrarERetornarToken("dias-fatura");
        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartaoRequest(
                                "Com dias", "Banco", Bandeira.VISA, "3579", null, null, false, null, true, 3, 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diaFechamento").value(3))
                .andExpect(jsonPath("$.data.diaVencimento").value(10));
        String principalId = criarCartaoERetornarId(token,
                new CartaoRequest("Principal", "Banco", Bandeira.ELO, "9753", null));

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartaoRequest(
                                "Adicional", "Banco", Bandeira.ELO, "9754", null, TipoCartao.ADICIONAL, false,
                                java.util.UUID.fromString(principalId), true, 3, 10))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartaoRequest(
                                "Dia invalido", "Banco", Bandeira.AMEX, "9755", null, null, false, null, true, 32, 10))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_deveRetornar400_quandoAdicionalNaoInformaPrincipal() throws Exception {
        String token = registrarERetornarToken("adicional-sem-principal");
        CartaoRequest request = new CartaoRequest(
                "Adicional sem principal", "Banco", Bandeira.VISA, "8642", null,
                TipoCartao.ADICIONAL, false, null);

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_deveRetornar400_quandoAdicionalTentaUsarPrincipalDeOutroUsuario() throws Exception {
        String tokenDono = registrarERetornarToken("principal-dono");
        String tokenOutro = registrarERetornarToken("principal-outro");
        String principalId = criarCartaoERetornarId(tokenDono,
                new CartaoRequest("Principal privado", "Banco", Bandeira.VISA, "8641", null));
        CartaoRequest adicional = new CartaoRequest(
                "Adicional", "Banco", Bandeira.VISA, "8642", null,
                TipoCartao.ADICIONAL, false, java.util.UUID.fromString(principalId));

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + tokenOutro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adicional)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_deveRetornar400_quandoUltimosDigitosInvalidos() throws Exception {
        String token = registrarERetornarToken("invalido");
        CartaoRequest request = new CartaoRequest("Cartao", "Banco", Bandeira.VISA, "12a4", null);

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_deveRetornar409_quandoBandeiraEDigitosDuplicadosParaMesmoUsuario() throws Exception {
        String token = registrarERetornarToken("dup");
        CartaoRequest request = new CartaoRequest("Cartao 1", "Banco", Bandeira.ELO, "5555", null);
        criarCartaoERetornarId(token, request);

        CartaoRequest duplicado = new CartaoRequest("Cartao 2", "Outro Banco", Bandeira.ELO, "5555", null);
        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_deveRetornar409_quandoUltrapassa20Cartoes() throws Exception {
        String token = registrarERetornarToken("limite");
        for (int i = 0; i < 20; i++) {
            CartaoRequest request = new CartaoRequest("Cartao " + i, "Banco", Bandeira.OUTRO, String.format("%04d", i), null);
            criarCartaoERetornarId(token, request);
        }

        CartaoRequest vigesimoPrimeiro = new CartaoRequest("Cartao 21", "Banco", Bandeira.OUTRO, "9998", null);
        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vigesimoPrimeiro)))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_deveFuncionarParaDoisUsuarios_quandoBandeiraEDigitosIguais() throws Exception {
        String tokenA = registrarERetornarToken("usera");
        String tokenB = registrarERetornarToken("userb");
        CartaoRequest request = new CartaoRequest("Cartao Igual", "Banco", Bandeira.AMEX, "4321", null);

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/cartoes")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void buscarPorId_deveRetornar404_quandoCartaoPertenceAOutroUsuario() throws Exception {
        String tokenA = registrarERetornarToken("dona");
        String tokenB = registrarERetornarToken("outra");
        String id = criarCartaoERetornarId(tokenA, new CartaoRequest("Cartao", "Banco", Bandeira.HIPERCARD, "1111", null));

        mockMvc.perform(get("/api/cartoes/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_deveRetornar404_quandoCartaoPertenceAOutroUsuario() throws Exception {
        String tokenA = registrarERetornarToken("dona2");
        String tokenB = registrarERetornarToken("outra2");
        String id = criarCartaoERetornarId(tokenA, new CartaoRequest("Cartao", "Banco", Bandeira.HIPERCARD, "2222", null));

        CartaoRequest atualizacao = new CartaoRequest("Novo Nome", "Novo Banco", Bandeira.VISA, "3333", null);
        mockMvc.perform(put("/api/cartoes/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizacao)))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluir_deveRetornar404_quandoCartaoPertenceAOutroUsuario() throws Exception {
        String tokenA = registrarERetornarToken("dona3");
        String tokenB = registrarERetornarToken("outra3");
        String id = criarCartaoERetornarId(tokenA, new CartaoRequest("Cartao", "Banco", Bandeira.HIPERCARD, "4444", null));

        mockMvc.perform(delete("/api/cartoes/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_deveRetornarApenasCartoesDoUsuarioAutenticado() throws Exception {
        String tokenA = registrarERetornarToken("lista-a");
        String tokenB = registrarERetornarToken("lista-b");
        criarCartaoERetornarId(tokenA, new CartaoRequest("Cartao A1", "Banco", Bandeira.VISA, "0001", null));
        criarCartaoERetornarId(tokenA, new CartaoRequest("Cartao A2", "Banco", Bandeira.ELO, "0002", null));
        criarCartaoERetornarId(tokenB, new CartaoRequest("Cartao B1", "Banco", Bandeira.VISA, "0001", null));

        mockMvc.perform(get("/api/cartoes").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/cartoes").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void atualizar_deveAtualizarCamposDoCartao_quandoDadosValidos() throws Exception {
        String token = registrarERetornarToken("update");
        String id = criarCartaoERetornarId(token, new CartaoRequest("Cartao Antigo", "Banco Antigo", Bandeira.VISA, "1010", null));

        CartaoRequest atualizacao = new CartaoRequest("Cartao Novo", "Banco Novo", Bandeira.MASTERCARD, "2020", "#000000");
        mockMvc.perform(put("/api/cartoes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizacao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Cartao Novo"))
                .andExpect(jsonPath("$.data.banco").value("Banco Novo"))
                .andExpect(jsonPath("$.data.bandeira").value("MASTERCARD"))
                .andExpect(jsonPath("$.data.ultimosDigitos").value("2020"))
                .andExpect(jsonPath("$.data.cor").value("#000000"));
    }

    @Test
    void atualizar_devePermitirInativarCartaoEExcluirPrincipaisInativosDoSeletor() throws Exception {
        String token = registrarERetornarToken("cartao-inativo");
        String id = criarCartaoERetornarId(
                token, new CartaoRequest("Cartão", "Banco", Bandeira.VISA, "8080", null));
        CartaoRequest inativacao = new CartaoRequest(
                "Cartão", "Banco", Bandeira.VISA, "8080", null, TipoCartao.PRINCIPAL, false, null, false);

        mockMvc.perform(put("/api/cartoes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inativacao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(false));

        mockMvc.perform(get("/api/cartoes/principais").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/cartoes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ativo").value(false));
    }

    @Test
    void excluir_deveRemoverCartao_eBuscaSubsequenteRetorna404() throws Exception {
        String token = registrarERetornarToken("delete");
        String id = criarCartaoERetornarId(token, new CartaoRequest("Cartao", "Banco", Bandeira.OUTRO, "6767", null));

        mockMvc.perform(delete("/api/cartoes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cartoes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_deveRetornar401_quandoNaoAutenticado() throws Exception {
        mockMvc.perform(get("/api/cartoes")).andExpect(status().isUnauthorized());
    }
}
