package br.com.rdrtech.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.rdrtech.bucket.Bucket;
import br.com.rdrtech.bucket.BucketRepository;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.dashboard.dto.DashboardResponse;
import br.com.rdrtech.pessoa.Pessoa;
import br.com.rdrtech.pessoa.PessoaRepository;
import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import br.com.rdrtech.saida.Saida;
import br.com.rdrtech.saida.SaidaParcela;
import br.com.rdrtech.saida.SaidaRepository;
import br.com.rdrtech.saida.SaidaResponsavel;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import br.com.rdrtech.usuario.Usuario;
import br.com.rdrtech.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@SpringBootTest
class DashboardCalculationIT {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private SubcategoriaRepository subcategoriaRepository;
    @Autowired
    private BucketRepository bucketRepository;
    @Autowired
    private PessoaRepository pessoaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private SaidaRepository saidaRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consultar_deveSomarSomentePartesDoUsuarioEIsolarDadosDeOutroTenant() {
        Usuario usuario = criarUsuario();
        autenticarComo(usuario.getId());
        Categoria categoria = criarCategoria("Saúde");
        Subcategoria subcategoria = criarSubcategoria(categoria, "Medicamentos");
        Pessoa pessoa = pessoaRepository.saveAndFlush(Pessoa.builder().nome("Maria").ativo(true).build());

        salvarSaida(usuario, pessoa, categoria, subcategoria, false, LocalDate.of(2026, 10, 15),
                new BigDecimal("100.00"), new BigDecimal("75.00"), new BigDecimal("25.00"));
        salvarSaida(usuario, pessoa, categoria, subcategoria, true, LocalDate.of(2026, 4, 15),
                new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("50.00"));
        salvarSaida(usuario, pessoa, categoria, subcategoria, true, LocalDate.of(2026, 8, 15),
                new BigDecimal("100.00"), new BigDecimal("40.00"), new BigDecimal("60.00"));
        salvarSaida(usuario, pessoa, categoria, subcategoria, true, LocalDate.of(2026, 10, 20),
                new BigDecimal("50.00"), new BigDecimal("45.00"), new BigDecimal("5.00"));
        salvarSaida(usuario, null, categoria, subcategoria, true, LocalDate.of(2026, 9, 15),
                new BigDecimal("0.03"), new BigDecimal("0.03"), null);
        salvarSaida(usuario, null, categoria, subcategoria, true, LocalDate.of(2026, 9, 20),
                new BigDecimal("0.03"), new BigDecimal("0.03"), null);
        bucketRepository.saveAndFlush(Bucket.builder()
                .subcategoria(subcategoria)
                .usuario(usuario)
                .valorMensal(new BigDecimal("200.00"))
                .build());
        bucketRepository.saveAndFlush(Bucket.builder()
                .subcategoria(subcategoria)
                .pessoa(pessoa)
                .valorMensal(new BigDecimal("500.00"))
                .build());

        Usuario outroUsuario = criarUsuario();
        autenticarComo(outroUsuario.getId());
        Categoria outraCategoria = criarCategoria("Transporte");
        Subcategoria outraSubcategoria = criarSubcategoria(outraCategoria, "Ônibus");
        salvarSaida(outroUsuario, null, outraCategoria, outraSubcategoria, false,
                LocalDate.of(2026, 10, 15), new BigDecimal("900.00"), new BigDecimal("900.00"), null);
        bucketRepository.saveAndFlush(Bucket.builder()
                .subcategoria(outraSubcategoria)
                .usuario(outroUsuario)
                .valorMensal(new BigDecimal("800.00"))
                .build());

        autenticarComo(usuario.getId());
        DashboardResponse response = dashboardService.consultar(10, 2026);

        assertThat(response.gastosLancados()).isEqualByComparingTo("120.00");
        assertThat(response.bucketsReservados()).isEqualByComparingTo("200.00");
        assertThat(response.contasFixasMesAnterior()).isEqualByComparingTo("0.06");
        assertThat(response.totalPrevisto()).isEqualByComparingTo("320.06");

        var saidas = dashboardService.listarSaidas(10, 2026);
        assertThat(saidas).hasSize(2);
        assertThat(saidas.getFirst().descricao()).isEqualTo("Despesa 2026-10-15");
        assertThat(saidas.getFirst().categoriaNome()).isEqualTo("Saúde");
        assertThat(saidas.getFirst().subcategoriaNome()).isEqualTo("Medicamentos");
        assertThat(saidas.getFirst().valor()).isEqualByComparingTo("75.00");
        assertThat(saidas.stream().map(item -> item.valor()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(response.gastosLancados());
        assertThat(saidas.getFirst().numeroParcela()).isEqualTo(1);

        var buckets = dashboardService.listarBuckets();
        assertThat(buckets).hasSize(1);
        assertThat(buckets.getFirst().categoriaNome()).isEqualTo("Saúde");
        assertThat(buckets.getFirst().subcategoriaNome()).isEqualTo("Medicamentos");
        assertThat(buckets.getFirst().valor()).isEqualByComparingTo("200.00");
        assertThat(buckets.stream().map(item -> item.valor()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(response.bucketsReservados());

        var contasFixas = dashboardService.listarContasFixasMesAnterior(10, 2026);
        assertThat(contasFixas).hasSize(2);
        assertThat(contasFixas).allSatisfy(item ->
                assertThat(item.valor()).isEqualByComparingTo("0.03"));
        assertThat(contasFixas.stream().map(item -> item.valor()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(response.contasFixasMesAnterior());

        var projecao = dashboardService.consultarProjecao(10, 2026, 3);
        assertThat(projecao).hasSize(3);
        assertThat(projecao.get(0).ano()).isEqualTo(2026);
        assertThat(projecao.get(0).mes()).isEqualTo(10);
        assertThat(projecao.get(0).gastosLancados()).isEqualByComparingTo("120.00");
        assertThat(projecao.get(0).contasFixasMesAnterior()).isEqualByComparingTo("0.06");
        assertThat(projecao.get(1).mes()).isEqualTo(11);
        assertThat(projecao.get(1).gastosLancados()).isEqualByComparingTo("0");
        assertThat(projecao.get(1).bucketsReservados()).isEqualByComparingTo("200.00");
        assertThat(projecao.get(1).contasFixasMesAnterior()).isEqualByComparingTo("45.00");
        assertThat(projecao.get(2).totalPrevisto()).isEqualByComparingTo("200.00");
    }

    @Test
    void consultarProjecao_deveUsarMesAnteriorEAvancarAno() {
        Usuario usuario = criarUsuario();
        autenticarComo(usuario.getId());
        Categoria categoria = criarCategoria("Casa");
        Subcategoria subcategoria = criarSubcategoria(categoria, "Serviços");
        Pessoa pessoa = pessoaRepository.saveAndFlush(Pessoa.builder().nome("Maria").ativo(true).build());

        salvarSaida(usuario, pessoa, categoria, subcategoria, true, LocalDate.of(2026, 11, 15),
                new BigDecimal("100.00"), new BigDecimal("80.00"), new BigDecimal("20.00"));
        salvarSaida(usuario, pessoa, categoria, subcategoria, true, LocalDate.of(2026, 12, 15),
                new BigDecimal("70.00"), new BigDecimal("60.00"), new BigDecimal("10.00"));
        salvarSaida(usuario, pessoa, categoria, subcategoria, false, LocalDate.of(2027, 1, 15),
                new BigDecimal("50.00"), new BigDecimal("35.00"), new BigDecimal("15.00"));
        bucketRepository.saveAndFlush(Bucket.builder()
                .subcategoria(subcategoria)
                .usuario(usuario)
                .valorMensal(new BigDecimal("10.00"))
                .build());

        var projecao = dashboardService.consultarProjecao(12, 2026, 3);

        assertThat(projecao).extracting("ano", "mes").containsExactly(
                org.assertj.core.groups.Tuple.tuple(2026, 12),
                org.assertj.core.groups.Tuple.tuple(2027, 1),
                org.assertj.core.groups.Tuple.tuple(2027, 2));
        assertThat(projecao.get(0).contasFixasMesAnterior()).isEqualByComparingTo("80.00");
        assertThat(projecao.get(1).contasFixasMesAnterior()).isEqualByComparingTo("60.00");
        assertThat(projecao.get(1).gastosLancados()).isEqualByComparingTo("35.00");
        assertThat(projecao.get(2).gastosLancados()).isEqualByComparingTo("0");
        assertThat(projecao.get(2).contasFixasMesAnterior()).isEqualByComparingTo("0");
        assertThat(projecao.get(2).totalPrevisto()).isEqualByComparingTo("10.00");
    }

    private Usuario criarUsuario() {
        UUID suffix = UUID.randomUUID();
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Conta")
                .sobrenome("Teste")
                .email(suffix + "@test.local")
                .senha("hash")
                .ativo(true)
                .build());
    }

    private Categoria criarCategoria(String nome) {
        return categoriaRepository.saveAndFlush(Categoria.builder()
                .nome(nome)
                .tipo(TipoCategoria.DESPESA)
                .ativo(true)
                .build());
    }

    private Subcategoria criarSubcategoria(Categoria categoria, String nome) {
        return subcategoriaRepository.saveAndFlush(Subcategoria.builder()
                .nome(nome)
                .tipo(TipoCategoria.DESPESA)
                .ativo(true)
                .categoria(categoria)
                .build());
    }

    private void salvarSaida(
            Usuario usuario,
            Pessoa pessoa,
            Categoria categoria,
            Subcategoria subcategoria,
            boolean contaFixa,
            LocalDate vencimento,
            BigDecimal valorParcela,
            BigDecimal parteUsuario,
            BigDecimal partePessoa) {
        Saida saida = Saida.builder()
                .descricao("Despesa " + vencimento)
                .contaFixa(contaFixa)
                .valorPorParcela(valorParcela)
                .quantidadeParcelas(1)
                .formaPagamento(FormaPagamento.PIX)
                .modoResponsabilidade(partePessoa == null ? ModoResponsabilidade.USUARIO : ModoResponsabilidade.DIVIDIR)
                .categoria(categoria)
                .subcategoria(subcategoria)
                .build();
        saida.getResponsabilidades().add(SaidaResponsavel.builder()
                .saida(saida)
                .usuario(usuario)
                .valorPorParcela(parteUsuario)
                .build());
        if (partePessoa != null) {
            saida.getResponsabilidades().add(SaidaResponsavel.builder()
                    .saida(saida)
                    .pessoa(pessoa)
                    .valorPorParcela(partePessoa)
                    .build());
        }
        saida.getParcelas().add(SaidaParcela.builder()
                .saida(saida)
                .numero(1)
                .valor(valorParcela)
                .vencimento(vencimento)
                .build());
        saidaRepository.saveAndFlush(saida);
    }

    private void autenticarComo(UUID usuarioId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(usuarioId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }
}
