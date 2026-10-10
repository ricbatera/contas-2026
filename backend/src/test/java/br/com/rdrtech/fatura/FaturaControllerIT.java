package br.com.rdrtech.fatura;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.cartao.Bandeira;
import br.com.rdrtech.cartao.TipoCartao;
import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FaturaControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registrar(String prefixoEmail) throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Ana", "Silva", prefixoEmail + "-" + System.nanoTime() + "@example.com", "Senha123!", null);
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

    private References criarReferencias(String token) throws Exception {
        String categoriaId = criar(
                token, "/api/categorias", new CategoriaRequest("Casa", TipoCategoria.DESPESA, true));
        String subcategoriaId = criar(
                token,
                "/api/categorias/" + categoriaId + "/subcategorias",
                new SubcategoriaCreateRequest("Compras", true));
        String cartaoPrincipalId = criar(
                token, "/api/cartoes", new CartaoRequest("Principal", "Banco", Bandeira.VISA, "1111", null));
        String cartaoAdicionalId = criar(
                token,
                "/api/cartoes",
                new CartaoRequest(
                        "Adicional",
                        "Banco",
                        Bandeira.VISA,
                        "2222",
                        null,
                        TipoCartao.ADICIONAL,
                        true,
                        UUID.fromString(cartaoPrincipalId)));
        return new References(categoriaId, subcategoriaId, cartaoPrincipalId, cartaoAdicionalId);
    }

    private SaidaRequest compra(
            References references, String cartaoId, String descricao, BigDecimal valor, YearMonth competencia) {
        return new SaidaRequest(
                descricao,
                "Fatura " + descricao,
                valor,
                1,
                FormaPagamento.CARTAO_CREDITO,
                null,
                competencia.atDay(15),
                UUID.fromString(cartaoId),
                UUID.fromString(references.categoriaId()),
                UUID.fromString(references.subcategoriaId()),
                ModoResponsabilidade.USUARIO,
                null,
                List.of());
    }

    private void registrarCompra(String token, SaidaRequest request) throws Exception {
        mockMvc.perform(post("/api/saidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void deveListarCompetenciasEExibirFaturaConsolidadaComParcelasDoAdicional() throws Exception {
        String token = registrar("fatura-consulta");
        References references = criarReferencias(token);
        YearMonth atual = YearMonth.now();
        YearMonth anterior = atual.minusMonths(1);
        YearMonth futura = atual.plusMonths(1);
        registrarCompra(token, compra(
                references,
                references.cartaoPrincipalId(),
                "Compra principal",
                new BigDecimal("50.00"),
                atual));
        registrarCompra(token, compra(
                references,
                references.cartaoAdicionalId(),
                "Compra adicional",
                new BigDecimal("100.00"),
                atual));
        registrarCompra(token, compra(
                references,
                references.cartaoAdicionalId(),
                "Compra passada",
                new BigDecimal("25.00"),
                anterior));
        registrarCompra(token, compra(
                references,
                references.cartaoAdicionalId(),
                "Compra futura",
                new BigDecimal("35.00"),
                futura));

        mockMvc.perform(get("/api/faturas/cartoes/" + references.cartaoPrincipalId() + "/competencias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].competencia").value(futura.getYear() * 100 + futura.getMonthValue()))
                .andExpect(jsonPath("$.data[0].total").value(35.00))
                .andExpect(jsonPath("$.data[1].competencia").value(atual.getYear() * 100 + atual.getMonthValue()))
                .andExpect(jsonPath("$.data[1].total").value(150.00))
                .andExpect(jsonPath("$.data[2].competencia").value(anterior.getYear() * 100 + anterior.getMonthValue()))
                .andExpect(jsonPath("$.data[2].total").value(25.00));

        mockMvc.perform(get("/api/faturas/cartoes/" + references.cartaoPrincipalId() + "/"
                                + (atual.getYear() * 100 + atual.getMonthValue()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartaoId").value(references.cartaoPrincipalId()))
                .andExpect(jsonPath("$.data.total").value(150.00))
                .andExpect(jsonPath("$.data.parcelas.length()").value(2))
                .andExpect(jsonPath("$.data.parcelas[0].descricao").value("Compra adicional"))
                .andExpect(jsonPath("$.data.parcelas[0].descricaoFatura").value("Fatura Compra adicional"))
                .andExpect(jsonPath("$.data.parcelas[0].cartaoId").value(references.cartaoAdicionalId()))
                .andExpect(jsonPath("$.data.parcelas[0].cartaoNome").value("Adicional"))
                .andExpect(jsonPath("$.data.parcelas[1].descricao").value("Compra principal"));

        mockMvc.perform(get("/api/faturas/cartoes/" + references.cartaoAdicionalId() + "/competencias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveIsolarConsultaDeFaturasPorTenantEValidarCompetencia() throws Exception {
        String tokenDono = registrar("fatura-dono");
        String tokenOutro = registrar("fatura-outro");
        References references = criarReferencias(tokenDono);
        YearMonth competencia = YearMonth.now();
        registrarCompra(tokenDono, compra(
                references,
                references.cartaoPrincipalId(),
                "Compra privada",
                new BigDecimal("10.00"),
                competencia));
        String path = "/api/faturas/cartoes/" + references.cartaoPrincipalId();

        mockMvc.perform(get(path + "/competencias").header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(path + "/" + (competencia.getYear() * 100 + competencia.getMonthValue()))
                        .header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(path + "/202613").header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path + "/202601"))
                .andExpect(status().isUnauthorized());
    }

    private record References(
            String categoriaId, String subcategoriaId, String cartaoPrincipalId, String cartaoAdicionalId) {
    }
}
