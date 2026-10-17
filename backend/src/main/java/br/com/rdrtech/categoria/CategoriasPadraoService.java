package br.com.rdrtech.categoria;

import br.com.rdrtech.subcategoria.Subcategoria;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria as categorias e subcategorias padrão de um usuário recém-cadastrado. Roda sem usuário
 * autenticado no contexto, por isso define o tenant explicitamente em cada entidade. Manter em
 * sincronia com o modelo de scripts/legacy/seed-categorias.sql (backfill de usuários antigos).
 */
@Service
@RequiredArgsConstructor
public class CategoriasPadraoService {

    private record Modelo(TipoCategoria tipo, String categoria, List<String> subcategorias) {}

    private static final List<Modelo> MODELOS = List.of(
            new Modelo(TipoCategoria.DESPESA, "Moradia",
                    List.of("Aluguel e financiamento", "Condomínio", "Manutenção e reparos", "Móveis e decoração")),
            new Modelo(TipoCategoria.DESPESA, "Alimentação",
                    List.of("Supermercado", "Restaurantes e delivery", "Lanches e cafés")),
            new Modelo(TipoCategoria.DESPESA, "Transporte",
                    List.of("Combustível", "Transporte público", "Aplicativos e táxi", "Manutenção e estacionamento")),
            new Modelo(TipoCategoria.DESPESA, "Saúde",
                    List.of("Plano de saúde", "Consultas e exames", "Medicamentos")),
            new Modelo(TipoCategoria.DESPESA, "Educação",
                    List.of("Mensalidades e cursos", "Livros e materiais")),
            new Modelo(TipoCategoria.DESPESA, "Lazer",
                    List.of("Passeios e eventos", "Assinaturas e streaming", "Esportes e hobbies")),
            new Modelo(TipoCategoria.DESPESA, "Compras",
                    List.of("Vestuário e acessórios", "Eletrônicos")),
            new Modelo(TipoCategoria.DESPESA, "Contas e serviços",
                    List.of("Água e esgoto", "Energia elétrica", "Internet e telefone", "Gás")),
            new Modelo(TipoCategoria.DESPESA, "Impostos e taxas",
                    List.of("Impostos", "Tarifas bancárias")),
            new Modelo(TipoCategoria.DESPESA, "Viagens",
                    List.of("Hospedagem", "Passagens", "Alimentação e passeios")),
            new Modelo(TipoCategoria.DESPESA, "Outros",
                    List.of("Despesas diversas")),
            new Modelo(TipoCategoria.RECEITA, "Salário",
                    List.of("Salário mensal", "Décimo terceiro", "Férias e bônus")),
            new Modelo(TipoCategoria.RECEITA, "Trabalho e serviços",
                    List.of("Freelance", "Serviços prestados")),
            new Modelo(TipoCategoria.RECEITA, "Vendas",
                    List.of("Produtos", "Bens usados")),
            new Modelo(TipoCategoria.RECEITA, "Investimentos",
                    List.of("Rendimentos", "Dividendos e juros")),
            new Modelo(TipoCategoria.RECEITA, "Benefícios",
                    List.of("Benefícios e auxílios")),
            new Modelo(TipoCategoria.RECEITA, "Reembolsos",
                    List.of("Reembolso de despesas")),
            new Modelo(TipoCategoria.RECEITA, "Aluguéis",
                    List.of("Aluguel recebido")),
            new Modelo(TipoCategoria.RECEITA, "Outros",
                    List.of("Receitas diversas")));

    private final CategoriaRepository categoriaRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void criarPara(UUID usuarioId) {
        List<Categoria> categorias = MODELOS.stream()
                .map(modelo -> montarCategoria(usuarioId, modelo))
                .toList();
        categoriaRepository.saveAll(categorias);
    }

    private Categoria montarCategoria(UUID usuarioId, Modelo modelo) {
        Categoria categoria = Categoria.builder()
                .nome(modelo.categoria())
                .tipo(modelo.tipo())
                .ativo(true)
                .build();
        categoria.definirTenant(usuarioId);
        modelo.subcategorias().forEach(nome -> {
            Subcategoria subcategoria = Subcategoria.builder()
                    .nome(nome)
                    .tipo(modelo.tipo())
                    .ativo(true)
                    .padrao(false)
                    .categoria(categoria)
                    .build();
            subcategoria.definirTenant(usuarioId);
            categoria.getSubcategorias().add(subcategoria);
        });
        return categoria;
    }
}
