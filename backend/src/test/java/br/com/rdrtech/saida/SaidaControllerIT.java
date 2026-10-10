package br.com.rdrtech.saida;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.cartao.Bandeira;
import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.pessoa.dto.PessoaRequest;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SaidaControllerIT {

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

    private ExpenseReferences criarReferencias(String token, boolean adicional) throws Exception {
        String categoriaId = postAndReadId(
                token,
                "/api/categorias",
                new CategoriaRequest("Casa", TipoCategoria.DESPESA, true));
        String subcategoriaId = postAndReadId(
                token,
                "/api/categorias/" + categoriaId + "/subcategorias",
                new SubcategoriaCreateRequest("Aluguel", true));
        String pessoaId = postAndReadId(
                token, "/api/pessoas", new PessoaRequest("Bruno", true));
        String principalId = postAndReadId(
                token,
                "/api/cartoes",
                new CartaoRequest("Principal", "Banco", Bandeira.VISA, "1111", null));
        String cartaoId = adicional
                ? postAndReadId(
                        token,
                        "/api/cartoes",
                        new CartaoRequest(
                                "Adicional",
                                "Banco",
                                Bandeira.VISA,
                                "2222",
                                null,
                                br.com.rdrtech.cartao.TipoCartao.ADICIONAL,
                                false,
                                UUID.fromString(principalId)))
                : principalId;
        return new ExpenseReferences(categoriaId, subcategoriaId, pessoaId, principalId, cartaoId);
    }

    private SaidaRequest requestCredito(
            ExpenseReferences references, BigDecimal valor, LocalDate vencimento, int parcelas, boolean contaFixa) {
        return new SaidaRequest(
                "Compra compartilhada",
                "Compra",
                valor,
                parcelas,
                FormaPagamento.CARTAO_CREDITO,
                null,
                vencimento,
                UUID.fromString(references.cartaoId()),
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.DIVIDIR,
                null,
                List.of(new SaidaParteRequest(UUID.fromString(references.pessoaId()), new BigDecimal("20.00"))),
                contaFixa);
    }

    @Test
    void comprasDeCartaoAdicionalEPrincipalDevemCompartilharFaturaPorCompetencia() throws Exception {
        String token = registrar("saida-fatura");
        ExpenseReferences references = criarReferencias(token, true);
        LocalDate vencimento = LocalDate.of(2026, 11, 15);
        SaidaRequest adicional = requestCredito(references, new BigDecimal("100.00"), vencimento, 2, true);

        MvcResult primeira = mockMvc.perform(post("/api/saidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adicional)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parcelas.length()").value(2))
                .andExpect(jsonPath("$.data.parcelas[0].vencimento").value("2026-11-15"))
                .andExpect(jsonPath("$.data.contaFixa").value(true))
                .andExpect(jsonPath("$.data.parcelas[1].vencimento").value("2026-12-15"))
                .andExpect(jsonPath("$.data.responsabilidades.length()").value(2))
                .andExpect(jsonPath("$.data.responsabilidades[0].valorPorParcela").value(20.00))
                .andExpect(jsonPath("$.data.responsabilidades[1].valorPorParcela").value(80.00))
                .andReturn();
        String saidaId = objectMapper.readTree(primeira.getResponse().getContentAsString()).at("/data/id").asText();
        String faturaNovembro = objectMapper.readTree(primeira.getResponse().getContentAsString())
                .at("/data/parcelas/0/faturaId").asText();
        mockMvc.perform(get("/api/saidas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].descricao").value("Compra compartilhada"));

        SaidaRequest principal = new SaidaRequest(
                "Outra compra",
                null,
                new BigDecimal("50.00"),
                1,
                FormaPagamento.CARTAO_CREDITO,
                null,
                vencimento,
                UUID.fromString(references.principalId()),
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.USUARIO,
                null,
                List.of());
        mockMvc.perform(post("/api/saidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(principal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parcelas[0].faturaId").value(faturaNovembro))
                .andExpect(jsonPath("$.data.parcelas[0].totalFatura").value(150.00));

        mockMvc.perform(put("/api/saidas/" + saidaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestCredito(
                                references, new BigDecimal("80.00"), vencimento, 1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contaFixa").value(false))
                .andExpect(jsonPath("$.data.parcelas.length()").value(1))
                .andExpect(jsonPath("$.data.parcelas[0].totalFatura").value(130.00));

        mockMvc.perform(delete("/api/saidas/" + saidaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/saidas/" + saidaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void boletoPixDeveGerarParcelasMensaisSemFaturas() throws Exception {
        String token = registrar("saida-pix");
        ExpenseReferences references = criarReferencias(token, false);
        SaidaRequest request = new SaidaRequest(
                "Conta de energia",
                null,
                new BigDecimal("75.50"),
                3,
                FormaPagamento.PIX,
                LocalDate.of(2026, 1, 31),
                null,
                null,
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.PESSOA,
                UUID.fromString(references.pessoaId()),
                List.of());

        mockMvc.perform(post("/api/saidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.valorTotal").value(226.50))
                .andExpect(jsonPath("$.data.responsabilidades.length()").value(1))
                .andExpect(jsonPath("$.data.responsabilidades[0].nome").value("Bruno"))
                .andExpect(jsonPath("$.data.contaFixa").value(false))
                .andExpect(jsonPath("$.data.parcelas[0].vencimento").value("2026-01-31"))
                .andExpect(jsonPath("$.data.parcelas[1].vencimento").value("2026-02-28"))
                .andExpect(jsonPath("$.data.parcelas[2].vencimento").value("2026-03-31"))
                .andExpect(jsonPath("$.data.parcelas[0].faturaId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void deveRejeitarDivisaoAcimaDoValorDaParcela() throws Exception {
        String token = registrar("saida-divisao-invalida");
        ExpenseReferences references = criarReferencias(token, false);
        SaidaRequest request = new SaidaRequest(
                "Compra",
                null,
                new BigDecimal("10.00"),
                1,
                FormaPagamento.BOLETO,
                LocalDate.of(2026, 10, 9),
                null,
                null,
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.DIVIDIR,
                null,
                List.of(new SaidaParteRequest(UUID.fromString(references.pessoaId()), new BigDecimal("11.00"))));

        mockMvc.perform(post("/api/saidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saidasDevemSerIsoladasPorUsuarioAutenticado() throws Exception {
        String tokenA = registrar("saida-tenant-a");
        String tokenB = registrar("saida-tenant-b");
        ExpenseReferences references = criarReferencias(tokenA, false);
        SaidaRequest request = new SaidaRequest(
                "Despesa privada",
                null,
                new BigDecimal("25.00"),
                1,
                FormaPagamento.DEBITO,
                LocalDate.of(2026, 10, 9),
                null,
                null,
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.USUARIO,
                null,
                List.of());
        String response = mockMvc.perform(post("/api/saidas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).at("/data/id").asText();

        mockMvc.perform(get("/api/saidas").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/saidas/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/saidas/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void devePermitirEditarSaidaSemRemoverReferenciasQueForamInativadas() throws Exception {
        String token = registrar("saida-historico");
        ExpenseReferences references = criarReferencias(token, false);
        SaidaRequest request = new SaidaRequest(
                "Despesa original",
                null,
                new BigDecimal("100.00"),
                1,
                FormaPagamento.CARTAO_CREDITO,
                null,
                LocalDate.of(2026, 11, 15),
                UUID.fromString(references.cartaoId()),
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.PESSOA,
                UUID.fromString(references.pessoaId()),
                List.of());
        String response = mockMvc.perform(post("/api/saidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String saidaId = objectMapper.readTree(response).at("/data/id").asText();

        CartaoRequest cartaoInativo = new CartaoRequest(
                "Principal", "Banco", Bandeira.VISA, "1111", null,
                br.com.rdrtech.cartao.TipoCartao.PRINCIPAL, false, null, false);
        mockMvc.perform(put("/api/cartoes/" + references.cartaoId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cartaoInativo)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/categorias/" + references.categoriaId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoriaRequest("Casa", TipoCategoria.DESPESA, false))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/pessoas/" + references.pessoaId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PessoaRequest("Bruno", false))))
                .andExpect(status().isOk());

        SaidaRequest update = new SaidaRequest(
                "Despesa atualizada",
                null,
                new BigDecimal("100.00"),
                1,
                FormaPagamento.CARTAO_CREDITO,
                null,
                LocalDate.of(2026, 11, 15),
                UUID.fromString(references.cartaoId()),
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.PESSOA,
                UUID.fromString(references.pessoaId()),
                List.of());
        mockMvc.perform(put("/api/saidas/" + saidaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.descricao").value("Despesa atualizada"))
                .andExpect(jsonPath("$.data.cartaoId").value(references.cartaoId()))
                .andExpect(jsonPath("$.data.categoriaId").value(references.categoriaId()))
                .andExpect(jsonPath("$.data.responsabilidades[0].pessoaId").value(references.pessoaId()));
    }

    private record ExpenseReferences(
            String categoriaId, String subcategoriaId, String pessoaId, String principalId, String cartaoId) {
    }
}
