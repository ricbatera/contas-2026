package br.com.rdrtech.categoria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CategoriaControllerIT {

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

    private String criarCategoria(String token, String nome, TipoCategoria tipo) throws Exception {
        CategoriaRequest request = new CategoriaRequest(nome, tipo, true);
        String body = mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    private String criarSubcategoria(String token, String categoryId, String nome) throws Exception {
        SubcategoriaCreateRequest request = new SubcategoriaCreateRequest(nome, true);
        String body = mockMvc.perform(post("/api/categorias/" + categoryId + "/subcategorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    @Test
    void fluxoCompleto_deveGerenciarCategoriaESubcategoriaEExcluirEmCascata() throws Exception {
        String token = registrarERetornarToken("categoria-crud");
        String categoryId = criarCategoria(token, "Moradia", TipoCategoria.DESPESA);
        String subcategoryId = criarSubcategoria(token, categoryId, "Aluguel");

        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subcategorias[0].nome").value("Aluguel"));

        CategoriaRequest update = new CategoriaRequest("Casa", TipoCategoria.DESPESA, false);
        mockMvc.perform(put("/api/categorias/" + categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Casa"))
                .andExpect(jsonPath("$.data.ativo").value(false))
                .andExpect(jsonPath("$.data.subcategorias[0].nome").value("Aluguel"));

        SubcategoriaRequest subcategoryUpdate =
                new SubcategoriaRequest("Aluguel residencial", UUID.fromString(categoryId), false);
        mockMvc.perform(put("/api/subcategorias/" + subcategoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subcategoryUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Aluguel residencial"))
                .andExpect(jsonPath("$.data.ativo").value(false));

        mockMvc.perform(delete("/api/categorias/" + categoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/subcategorias/" + subcategoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoriasESubcategoriasDevemSerIsoladasPorTenant() throws Exception {
        String tokenA = registrarERetornarToken("categoria-tenant-a");
        String tokenB = registrarERetornarToken("categoria-tenant-b");
        String categoryId = criarCategoria(tokenA, "Privada", TipoCategoria.RECEITA);
        String subcategoryId = criarSubcategoria(tokenA, categoryId, "Somente A");

        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/categorias/" + categoryId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/subcategorias/" + subcategoryId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/subcategorias/" + subcategoryId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoDevePermitirNomesDuplicadosNoMesmoTipoOuCategoria() throws Exception {
        String token = registrarERetornarToken("categoria-duplicada");
        String categoryId = criarCategoria(token, "Moradia", TipoCategoria.DESPESA);
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoriaRequest("moradia", TipoCategoria.DESPESA, true))))
                .andExpect(status().isConflict());

        criarSubcategoria(token, categoryId, "Aluguel");
        SubcategoriaRequest duplicate = new SubcategoriaRequest("aluguel", UUID.fromString(categoryId), true);
        mockMvc.perform(post("/api/categorias/" + categoryId + "/subcategorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void deveManterUmaSubcategoriaPadraoPorTipoEOrdenarCategoriaPaiNoTopo() throws Exception {
        String token = registrarERetornarToken("categoria-default");
        String moradia = criarCategoria(token, "Moradia", TipoCategoria.DESPESA);
        String alimentacao = criarCategoria(token, "Alimentação", TipoCategoria.DESPESA);
        String salario = criarCategoria(token, "Salário", TipoCategoria.RECEITA);
        String aluguel = criarSubcategoria(token, moradia, "Aluguel");
        String supermercado = criarSubcategoria(token, alimentacao, "Supermercado");
        String salarioMensal = criarSubcategoria(token, salario, "Salário mensal");

        SubcategoriaRequest aluguelDefault =
                new SubcategoriaRequest("Aluguel", UUID.fromString(moradia), true, true);
        mockMvc.perform(put("/api/subcategorias/" + aluguel)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aluguelDefault)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.padrao").value(true));

        SubcategoriaRequest mercadoDefault =
                new SubcategoriaRequest("Supermercado", UUID.fromString(alimentacao), true, true);
        mockMvc.perform(put("/api/subcategorias/" + supermercado)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mercadoDefault)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.padrao").value(true));

        SubcategoriaRequest receitaDefault =
                new SubcategoriaRequest("Salário mensal", UUID.fromString(salario), true, true);
        mockMvc.perform(put("/api/subcategorias/" + salarioMensal)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receitaDefault)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.padrao").value(true));

        mockMvc.perform(get("/api/subcategorias/" + aluguel).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.padrao").value(false));
        mockMvc.perform(get("/api/subcategorias/" + supermercado).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.padrao").value(true));

        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(alimentacao))
                .andExpect(jsonPath("$.data[0].padrao").value(true))
                .andExpect(jsonPath("$.data[1].id").value(salario))
                .andExpect(jsonPath("$.data[1].padrao").value(true))
                .andExpect(jsonPath("$.data[2].id").value(moradia))
                .andExpect(jsonPath("$.data[2].padrao").value(false));
    }

    @Test
    void categoriaDeveExigirCamposValidosEAutenticacao() throws Exception {
        String token = registrarERetornarToken("categoria-invalida");
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" \",\"tipo\":\"DESPESA\",\"ativo\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/categorias")).andExpect(status().isUnauthorized());
    }
}
