package br.com.rdrtech.bucket;

import br.com.rdrtech.bucket.dto.BucketRequest;
import br.com.rdrtech.bucket.dto.BucketResponse;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.exception.BucketDuplicadoException;
import br.com.rdrtech.exception.BucketInvalidoException;
import br.com.rdrtech.exception.BucketNaoEncontradoException;
import br.com.rdrtech.exception.PessoaNaoEncontradaException;
import br.com.rdrtech.exception.SubcategoriaNaoEncontradaException;
import br.com.rdrtech.pessoa.Pessoa;
import br.com.rdrtech.pessoa.PessoaRepository;
import br.com.rdrtech.saida.SaidaResponsavelRepository;
import br.com.rdrtech.security.SecurityUtils;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import br.com.rdrtech.usuario.Usuario;
import br.com.rdrtech.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BucketService {

    private final BucketRepository bucketRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SaidaResponsavelRepository saidaResponsavelRepository;

    public List<BucketResponse> listar(int mes, int ano) {
        LocalDate inicio = primeiroDia(mes, ano);
        LocalDate fim = inicio.plusMonths(1);
        return bucketRepository.listarComReferencias().stream()
                .sorted(Comparator.comparing((Bucket bucket) -> bucket.getSubcategoria().getCategoria().getNome(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(bucket -> bucket.getSubcategoria().getNome(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(bucket -> responsavelNome(bucket), String.CASE_INSENSITIVE_ORDER))
                .map(bucket -> BucketMapper.toResponse(bucket, calcularConsumo(bucket, inicio, fim), mes, ano))
                .toList();
    }

    public BucketResponse buscarPorId(UUID id) {
        Bucket bucket = bucketRepository.buscarPorId(id).orElseThrow(BucketNaoEncontradoException::new);
        LocalDate hoje = LocalDate.now();
        return BucketMapper.toResponse(bucket, BigDecimal.ZERO, hoje.getMonthValue(), hoje.getYear());
    }

    @Transactional
    public BucketResponse criar(BucketRequest request) {
        Bucket bucket = new Bucket();
        aplicarRequest(bucket, request, null);
        LocalDate hoje = LocalDate.now();
        return BucketMapper.toResponse(
                bucketRepository.save(bucket), BigDecimal.ZERO, hoje.getMonthValue(), hoje.getYear());
    }

    @Transactional
    public BucketResponse atualizar(UUID id, BucketRequest request) {
        Bucket bucket = bucketRepository.buscarPorId(id).orElseThrow(BucketNaoEncontradoException::new);
        aplicarRequest(bucket, request, id);
        LocalDate hoje = LocalDate.now();
        return BucketMapper.toResponse(
                bucketRepository.save(bucket), BigDecimal.ZERO, hoje.getMonthValue(), hoje.getYear());
    }

    @Transactional
    public void excluir(UUID id) {
        Bucket bucket = bucketRepository.buscarPorId(id).orElseThrow(BucketNaoEncontradoException::new);
        bucketRepository.delete(bucket);
    }

    private void aplicarRequest(Bucket bucket, BucketRequest request, UUID idAtual) {
        Subcategoria subcategoria = subcategoriaRepository.buscarPorId(request.subcategoriaId())
                .filter(item -> item.isAtivo()
                        && item.getCategoria().isAtivo()
                        && item.getTipo() == TipoCategoria.DESPESA
                        && item.getCategoria().getTipo() == TipoCategoria.DESPESA)
                .orElseThrow(SubcategoriaNaoEncontradaException::new);
        Pessoa pessoa = null;
        Usuario usuario = null;
        if (request.responsavelPessoaId() == null) {
            UUID usuarioId = SecurityUtils.getCurrentUserId()
                    .orElseThrow(() -> new BucketInvalidoException("Não foi possível identificar o usuário autenticado."));
            usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new BucketInvalidoException("Usuário autenticado não encontrado."));
            if (bucketRepository.existeParaUsuario(subcategoria.getId(), usuarioId, idAtual)) {
                throw new BucketDuplicadoException();
            }
        } else {
            pessoa = pessoaRepository.buscarPorId(request.responsavelPessoaId())
                    .filter(Pessoa::isAtivo)
                    .orElseThrow(PessoaNaoEncontradaException::new);
            if (bucketRepository.existeParaPessoa(subcategoria.getId(), pessoa.getId(), idAtual)) {
                throw new BucketDuplicadoException();
            }
        }
        bucket.setSubcategoria(subcategoria);
        bucket.setPessoa(pessoa);
        bucket.setUsuario(usuario);
        bucket.setValorMensal(request.valorMensal());
    }

    private BigDecimal calcularConsumo(Bucket bucket, LocalDate inicio, LocalDate fim) {
        return bucket.getPessoa() != null
                ? saidaResponsavelRepository.somarPartePessoa(
                        bucket.getSubcategoria().getId(), bucket.getPessoa().getId(), inicio, fim)
                : saidaResponsavelRepository.somarParteUsuario(
                        bucket.getSubcategoria().getId(), bucket.getUsuario().getId(), inicio, fim);
    }

    private LocalDate primeiroDia(int mes, int ano) {
        try {
            return LocalDate.of(ano, mes, 1);
        } catch (DateTimeException exception) {
            throw new BucketInvalidoException("Informe uma competência válida.");
        }
    }

    private String responsavelNome(Bucket bucket) {
        return bucket.getPessoa() != null
                ? bucket.getPessoa().getNome()
                : bucket.getUsuario().getNome() + " " + bucket.getUsuario().getSobrenome();
    }
}
