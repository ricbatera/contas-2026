package br.com.rdrtech.bucket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.rdrtech.bucket.dto.BucketRequest;
import br.com.rdrtech.bucket.dto.BucketResponse;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.exception.BucketDuplicadoException;
import br.com.rdrtech.exception.BucketInvalidoException;
import br.com.rdrtech.pessoa.Pessoa;
import br.com.rdrtech.pessoa.PessoaRepository;
import br.com.rdrtech.saida.SaidaResponsavelRepository;
import br.com.rdrtech.security.SecurityUtils;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import br.com.rdrtech.usuario.Usuario;
import br.com.rdrtech.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    @Mock
    private BucketRepository bucketRepository;
    @Mock
    private SubcategoriaRepository subcategoriaRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SaidaResponsavelRepository saidaResponsavelRepository;

    private BucketService service;
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID categoriaId = UUID.randomUUID();
    private final UUID subcategoriaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BucketService(
                bucketRepository,
                subcategoriaRepository,
                pessoaRepository,
                usuarioRepository,
                saidaResponsavelRepository);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(usuarioId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listar_deveSomarParticipacaoDoResponsavelNaCompetencia() {
        Bucket bucket = bucketUsuario();
        when(bucketRepository.listarComReferencias()).thenReturn(List.of(bucket));
        when(saidaResponsavelRepository.somarParteUsuario(
                        subcategoriaId, usuarioId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(new BigDecimal("125.50"));

        List<BucketResponse> response = service.listar(10, 2026);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().valorConsumido()).isEqualByComparingTo("125.50");
        assertThat(response.getFirst().saldo()).isEqualByComparingTo("74.50");
        assertThat(response.getFirst().percentualConsumido()).isEqualByComparingTo("62.75");
        assertThat(response.getFirst().responsavelUsuario()).isTrue();
        verify(saidaResponsavelRepository).somarParteUsuario(
                subcategoriaId, usuarioId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
    }

    @Test
    void listar_deveSomarParteDaPessoaResponsavel() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().nome("Maria").ativo(true).build();
        pessoa.setId(pessoaId);
        Bucket bucket = bucketPessoa(pessoa);
        when(bucketRepository.listarComReferencias()).thenReturn(List.of(bucket));
        when(saidaResponsavelRepository.somarPartePessoa(
                        subcategoriaId, pessoaId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(new BigDecimal("210.00"));

        BucketResponse response = service.listar(10, 2026).getFirst();

        assertThat(response.valorConsumido()).isEqualByComparingTo("210.00");
        assertThat(response.saldo()).isEqualByComparingTo("-10.00");
        assertThat(response.percentualConsumido()).isEqualByComparingTo("105.00");
        assertThat(response.responsavelPessoaId()).isEqualTo(pessoaId);
        assertThat(response.responsavelUsuario()).isFalse();
    }

    @Test
    void listar_deveRejeitarCompetenciaInvalida() {
        assertThatThrownBy(() -> service.listar(13, 2026))
                .isInstanceOf(BucketInvalidoException.class);
    }

    @Test
    void criar_deveAssociarBucketAoUsuarioAutenticado() {
        Subcategoria subcategoria = subcategoria();
        Usuario usuario = Usuario.builder().nome("Ana").sobrenome("Silva").build();
        when(subcategoriaRepository.buscarPorId(subcategoriaId)).thenReturn(Optional.of(subcategoria));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(bucketRepository.existeParaUsuario(subcategoriaId, usuarioId, null)).thenReturn(false);
        when(bucketRepository.save(any(Bucket.class))).thenAnswer(invocation -> {
            Bucket bucket = invocation.getArgument(0);
            bucket.setId(UUID.randomUUID());
            return bucket;
        });

        BucketResponse response = service.criar(new BucketRequest(
                subcategoriaId, null, new BigDecimal("200.00")));

        assertThat(response.subcategoriaId()).isEqualTo(subcategoriaId);
        assertThat(response.responsavelNome()).isEqualTo("Ana Silva");
        assertThat(response.valorMensal()).isEqualByComparingTo("200.00");
        verify(bucketRepository).save(any(Bucket.class));
    }

    @Test
    void criar_deveRejeitarBucketDuplicadoParaResponsavel() {
        Subcategoria subcategoria = subcategoria();
        when(subcategoriaRepository.buscarPorId(subcategoriaId)).thenReturn(Optional.of(subcategoria));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(new Usuario()));
        when(bucketRepository.existeParaUsuario(subcategoriaId, usuarioId, null)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new BucketRequest(
                        subcategoriaId, null, new BigDecimal("200.00"))))
                .isInstanceOf(BucketDuplicadoException.class);
        verify(bucketRepository, never()).save(any());
    }

    @Test
    void atualizar_deveAtualizarLimiteDoBucketExistente() {
        UUID bucketId = UUID.randomUUID();
        Bucket bucket = bucketUsuario();
        when(bucketRepository.buscarPorId(bucketId)).thenReturn(Optional.of(bucket));
        when(subcategoriaRepository.buscarPorId(subcategoriaId)).thenReturn(Optional.of(subcategoria()));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(bucket.getUsuario()));
        when(bucketRepository.existeParaUsuario(subcategoriaId, usuarioId, bucketId)).thenReturn(false);
        when(bucketRepository.save(bucket)).thenReturn(bucket);

        BucketResponse response = service.atualizar(
                bucketId, new BucketRequest(subcategoriaId, null, new BigDecimal("350.00")));

        assertThat(response.valorMensal()).isEqualByComparingTo("350.00");
        verify(bucketRepository).save(bucket);
    }

    @Test
    void excluir_deveRemoverBucketExistente() {
        UUID bucketId = UUID.randomUUID();
        Bucket bucket = bucketUsuario();
        when(bucketRepository.buscarPorId(bucketId)).thenReturn(Optional.of(bucket));

        service.excluir(bucketId);

        verify(bucketRepository).delete(bucket);
    }

    private Bucket bucketUsuario() {
        Usuario usuario = Usuario.builder().nome("Ana").sobrenome("Silva").build();
        usuario.setId(usuarioId);
        return Bucket.builder()
                .subcategoria(subcategoria())
                .usuario(usuario)
                .valorMensal(new BigDecimal("200.00"))
                .build();
    }

    private Bucket bucketPessoa(Pessoa pessoa) {
        return Bucket.builder()
                .subcategoria(subcategoria())
                .pessoa(pessoa)
                .valorMensal(new BigDecimal("200.00"))
                .build();
    }

    private Subcategoria subcategoria() {
        Categoria categoria = Categoria.builder()
                .nome("Saúde")
                .tipo(TipoCategoria.DESPESA)
                .ativo(true)
                .build();
        categoria.setId(categoriaId);
        Subcategoria subcategoria = Subcategoria.builder()
                .nome("Medicamentos")
                .tipo(TipoCategoria.DESPESA)
                .ativo(true)
                .categoria(categoria)
                .build();
        subcategoria.setId(subcategoriaId);
        return subcategoria;
    }
}
