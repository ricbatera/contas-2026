package br.com.rdrtech.bucket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.rdrtech.bucket.dto.BucketRequest;
import br.com.rdrtech.bucket.dto.BucketResponse;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.exception.BucketDuplicadoException;
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
class BucketConsumptionTest {

    @Autowired
    private BucketService bucketService;
    @Autowired
    private BucketRepository bucketRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private SubcategoriaRepository subcategoriaRepository;
    @Autowired
    private PessoaRepository pessoaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private SaidaRepository saidaRepository;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listar_deveSomarSomenteParcelaDaCompetenciaEParteDoResponsavel() {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Ana")
                .sobrenome("Silva")
                .email(usuarioId + "@test.local")
                .senha("hash")
                .ativo(true)
                .build());
        autenticarComo(usuario.getId());

        Categoria categoria = categoriaRepository.saveAndFlush(Categoria.builder()
                .nome("Saúde")
                .tipo(TipoCategoria.DESPESA)
                .ativo(true)
                .build());
        Subcategoria subcategoria = subcategoriaRepository.saveAndFlush(Subcategoria.builder()
                .nome("Medicamentos")
                .tipo(TipoCategoria.DESPESA)
                .ativo(true)
                .categoria(categoria)
                .build());
        Pessoa pessoa = pessoaRepository.saveAndFlush(Pessoa.builder().nome("Maria").ativo(true).build());

        Saida saida = Saida.builder()
                .descricao("Compra parcelada")
                .contaFixa(false)
                .valorPorParcela(new BigDecimal("100.00"))
                .quantidadeParcelas(2)
                .formaPagamento(FormaPagamento.PIX)
                .dataInicial(LocalDate.of(2026, 10, 15))
                .modoResponsabilidade(ModoResponsabilidade.DIVIDIR)
                .categoria(categoria)
                .subcategoria(subcategoria)
                .build();
        saida.getResponsabilidades().add(SaidaResponsavel.builder()
                .saida(saida)
                .usuario(usuario)
                .valorPorParcela(new BigDecimal("75.00"))
                .build());
        saida.getResponsabilidades().add(SaidaResponsavel.builder()
                .saida(saida)
                .pessoa(pessoa)
                .valorPorParcela(new BigDecimal("25.00"))
                .build());
        saida.getParcelas().add(SaidaParcela.builder()
                .saida(saida)
                .numero(1)
                .valor(new BigDecimal("100.00"))
                .vencimento(LocalDate.of(2026, 10, 15))
                .build());
        saida.getParcelas().add(SaidaParcela.builder()
                .saida(saida)
                .numero(2)
                .valor(new BigDecimal("100.00"))
                .vencimento(LocalDate.of(2026, 11, 15))
                .build());
        saidaRepository.saveAndFlush(saida);

        bucketRepository.saveAndFlush(Bucket.builder()
                .subcategoria(subcategoria)
                .usuario(usuario)
                .valorMensal(new BigDecimal("200.00"))
                .build());
        bucketRepository.saveAndFlush(Bucket.builder()
                .subcategoria(subcategoria)
                .pessoa(pessoa)
                .valorMensal(new BigDecimal("100.00"))
                .build());

        var buckets = bucketService.listar(10, 2026);

        assertThat(buckets).hasSize(2);
        assertThat(buckets).filteredOn(BucketResponse::responsavelUsuario)
                .singleElement()
                .extracting(BucketResponse::valorConsumido)
                .isEqualTo(new BigDecimal("75.00"));
        assertThat(buckets).filteredOn(bucket -> !bucket.responsavelUsuario())
                .singleElement()
                .extracting(BucketResponse::valorConsumido)
                .isEqualTo(new BigDecimal("25.00"));
        assertThatThrownBy(() -> bucketService.criar(
                        new BucketRequest(subcategoria.getId(), null, new BigDecimal("300.00"))))
                .isInstanceOf(BucketDuplicadoException.class);
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
