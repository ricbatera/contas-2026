package br.com.rdrtech.saida;

import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.cartao.CartaoRepository;
import br.com.rdrtech.exception.CategoriaNaoEncontradaException;
import br.com.rdrtech.exception.PessoaNaoEncontradaException;
import br.com.rdrtech.exception.SaidaInvalidaException;
import br.com.rdrtech.exception.SaidaNaoEncontradaException;
import br.com.rdrtech.exception.SubcategoriaNaoEncontradaException;
import br.com.rdrtech.fatura.Fatura;
import br.com.rdrtech.fatura.FaturaRepository;
import br.com.rdrtech.pessoa.Pessoa;
import br.com.rdrtech.pessoa.PessoaRepository;
import br.com.rdrtech.saida.dto.SaidaParteRequest;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.saida.dto.SaidaResponse;
import br.com.rdrtech.security.SecurityUtils;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import br.com.rdrtech.usuario.Usuario;
import br.com.rdrtech.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaidaService {

    private final SaidaRepository saidaRepository;
    private final FaturaRepository faturaRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioRepository usuarioRepository;

    public List<SaidaResponse> listar() {
        return saidaRepository.findAll(Sort.by(Sort.Direction.DESC, "auditoria.createdAt")).stream()
                .map(SaidaMapper::toResponse)
                .toList();
    }

    public SaidaResponse buscarPorId(UUID id) {
        return saidaRepository.buscarPorId(id)
                .map(SaidaMapper::toResponse)
                .orElseThrow(SaidaNaoEncontradaException::new);
    }

    @Transactional
    public SaidaResponse criar(SaidaRequest request) {
        Saida saida = new Saida();
        aplicarRequest(saida, request, null);
        return SaidaMapper.toResponse(saidaRepository.save(saida));
    }

    @Transactional
    public SaidaResponse atualizar(UUID id, SaidaRequest request) {
        Saida saida = saidaRepository.buscarPorId(id).orElseThrow(SaidaNaoEncontradaException::new);
        ExistingReferences referencesAnteriores = new ExistingReferences(
                saida.getCartao() != null ? saida.getCartao().getId() : null,
                saida.getCategoria().getId(),
                saida.getSubcategoria().getId(),
                saida.getResponsabilidades().stream()
                        .filter(item -> item.getPessoa() != null)
                        .map(item -> item.getPessoa().getId())
                        .collect(java.util.stream.Collectors.toSet()));
        Set<Fatura> faturasAnteriores = new HashSet<>();
        for (SaidaParcela parcela : new ArrayList<>(saida.getParcelas())) {
            if (parcela.getFatura() != null) {
                faturasAnteriores.add(parcela.getFatura());
                parcela.getFatura().removerParcela(parcela);
            }
        }
        saida.getParcelas().clear();
        saida.getResponsabilidades().clear();
        saidaRepository.flush();

        aplicarRequest(saida, request, referencesAnteriores);
        Saida atualizada = saidaRepository.save(saida);
        removerFaturasVazias(faturasAnteriores);
        return SaidaMapper.toResponse(atualizada);
    }

    @Transactional
    public void excluir(UUID id) {
        Saida saida = saidaRepository.buscarPorId(id).orElseThrow(SaidaNaoEncontradaException::new);
        Set<Fatura> faturasAnteriores = new HashSet<>();
        for (SaidaParcela parcela : new ArrayList<>(saida.getParcelas())) {
            if (parcela.getFatura() != null) {
                faturasAnteriores.add(parcela.getFatura());
                parcela.getFatura().removerParcela(parcela);
            }
        }
        saidaRepository.delete(saida);
        saidaRepository.flush();
        removerFaturasVazias(faturasAnteriores);
    }

    private void aplicarRequest(Saida saida, SaidaRequest request, ExistingReferences referencesAnteriores) {
        ValidatedReferences references = validarRequest(request, referencesAnteriores);
        saida.setDescricao(request.descricao().trim());
        saida.setDescricaoFatura(request.descricaoFatura() == null ? null : request.descricaoFatura().trim());
        saida.setContaFixa(request.contaFixa());
        saida.setValorPorParcela(request.valorPorParcela());
        saida.setQuantidadeParcelas(request.quantidadeParcelas());
        saida.setFormaPagamento(request.formaPagamento());
        saida.setModoResponsabilidade(request.modoResponsabilidade());
        saida.setDataInicial(request.dataInicial());
        saida.setPrimeiroVencimento(request.primeiroVencimento());
        saida.setCartao(references.cartao());
        saida.setCategoria(references.categoria());
        saida.setSubcategoria(references.subcategoria());

        adicionarResponsabilidades(saida, request, references.partes(), references.usuario());
        gerarParcelas(saida, references.cartao());
    }

    private ValidatedReferences validarRequest(SaidaRequest request, ExistingReferences referencesAnteriores) {
        boolean credito = request.formaPagamento() == FormaPagamento.CARTAO_CREDITO;
        Cartao cartao = null;
        if (credito) {
            if (request.cartaoId() == null || request.primeiroVencimento() == null || request.dataInicial() != null) {
                throw new SaidaInvalidaException(
                        "Informe um cartão e o vencimento da primeira parcela; não informe uma data inicial.");
            }
            cartao = cartaoRepository.buscarPorId(request.cartaoId())
                    .filter(item -> cartaoDisponivel(item)
                            || (referencesAnteriores != null
                                    && item.getId().equals(referencesAnteriores.cartaoId())))
                    .orElseThrow(() -> new SaidaInvalidaException("Selecione um cartão ativo."));
        } else if (request.cartaoId() != null
                || request.primeiroVencimento() != null
                || request.dataInicial() == null
                || request.descricaoFatura() != null && !request.descricaoFatura().isBlank()) {
            throw new SaidaInvalidaException(
                    "Informe a data da saída; cartão, vencimento de fatura e descrição de fatura são exclusivos de crédito.");
        }

        Categoria categoria = categoriaRepository.buscarPorId(request.categoriaId())
                .filter(item -> item.isAtivo()
                        || (referencesAnteriores != null
                                && item.getId().equals(referencesAnteriores.categoriaId())
                                && referencesAnteriores.subcategoriaId().equals(request.subcategoriaId())))
                .filter(item -> item.getTipo() == TipoCategoria.DESPESA
                        || (referencesAnteriores != null
                                && item.getId().equals(referencesAnteriores.categoriaId())))
                .orElseThrow(() -> new CategoriaNaoEncontradaException());
        Subcategoria subcategoria = subcategoriaRepository.buscarPorId(request.subcategoriaId())
                .filter(item -> item.isAtivo()
                        || (referencesAnteriores != null
                                && item.getId().equals(referencesAnteriores.subcategoriaId())
                                && referencesAnteriores.categoriaId().equals(categoria.getId())))
                .filter(item -> item.getCategoria().getId().equals(categoria.getId()))
                .orElseThrow(SubcategoriaNaoEncontradaException::new);

        if (request.modoResponsabilidade() == ModoResponsabilidade.PESSOA) {
            if (request.responsavelPessoaId() == null || !request.partes().isEmpty()) {
                throw new SaidaInvalidaException("Selecione uma pessoa responsável pela saída.");
            }
        } else if (request.responsavelPessoaId() != null) {
            throw new SaidaInvalidaException("A pessoa responsável direta só pode ser informada no modo pessoa.");
        }
        if (request.modoResponsabilidade() == ModoResponsabilidade.DIVIDIR && request.partes().isEmpty()) {
            throw new SaidaInvalidaException("Adicione ao menos uma pessoa para dividir a saída.");
        }
        if (request.modoResponsabilidade() != ModoResponsabilidade.DIVIDIR && !request.partes().isEmpty()) {
            throw new SaidaInvalidaException("As partes só podem ser informadas no modo dividir.");
        }

        List<Pessoa> partes = new ArrayList<>();
        Set<UUID> pessoaIds = new HashSet<>();
        BigDecimal soma = BigDecimal.ZERO;
        for (SaidaParteRequest parte : request.partes()) {
            if (!pessoaIds.add(parte.pessoaId())) {
                throw new SaidaInvalidaException("Cada pessoa pode aparecer apenas uma vez na divisão.");
            }
            Pessoa pessoa = pessoaRepository.buscarPorId(parte.pessoaId())
                    .filter(item -> item.isAtivo()
                            || possuiResponsabilidadeAnterior(referencesAnteriores, item.getId()))
                    .orElseThrow(PessoaNaoEncontradaException::new);
            partes.add(pessoa);
            soma = soma.add(parte.valorPorParcela());
        }
        if (soma.compareTo(request.valorPorParcela()) > 0) {
            throw new SaidaInvalidaException("A soma das partes não pode exceder o valor da parcela.");
        }
        if (request.modoResponsabilidade() == ModoResponsabilidade.PESSOA) {
            Pessoa pessoa = pessoaRepository.buscarPorId(request.responsavelPessoaId())
                    .filter(item -> item.isAtivo()
                            || possuiResponsabilidadeAnterior(referencesAnteriores, item.getId()))
                    .orElseThrow(PessoaNaoEncontradaException::new);
            partes.add(pessoa);
        }

        UUID usuarioId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new SaidaInvalidaException("Não foi possível identificar o usuário autenticado."));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new SaidaInvalidaException("Usuário autenticado não encontrado."));
        return new ValidatedReferences(cartao, categoria, subcategoria, partes, usuario);
    }

    private void adicionarResponsabilidades(
            Saida saida, SaidaRequest request, List<Pessoa> pessoas, Usuario usuario) {
        if (request.modoResponsabilidade() == ModoResponsabilidade.PESSOA) {
            saida.getResponsabilidades().add(SaidaResponsavel.builder()
                    .saida(saida)
                    .pessoa(pessoas.getFirst())
                    .valorPorParcela(request.valorPorParcela())
                    .build());
            return;
        }
        BigDecimal totalPessoas = BigDecimal.ZERO;
        for (int index = 0; index < request.partes().size(); index++) {
            SaidaParteRequest parte = request.partes().get(index);
            totalPessoas = totalPessoas.add(parte.valorPorParcela());
            saida.getResponsabilidades().add(SaidaResponsavel.builder()
                    .saida(saida)
                    .pessoa(pessoas.get(index))
                    .valorPorParcela(parte.valorPorParcela())
                    .build());
        }
        BigDecimal minhaParte = request.valorPorParcela().subtract(totalPessoas);
        if (request.modoResponsabilidade() == ModoResponsabilidade.USUARIO || minhaParte.signum() > 0) {
            saida.getResponsabilidades().add(SaidaResponsavel.builder()
                    .saida(saida)
                    .usuario(usuario)
                    .valorPorParcela(request.modoResponsabilidade() == ModoResponsabilidade.USUARIO
                            ? request.valorPorParcela()
                            : minhaParte)
                    .build());
        }
    }

    private void gerarParcelas(Saida saida, Cartao cartao) {
        LocalDate dataBase = saida.getFormaPagamento() == FormaPagamento.CARTAO_CREDITO
                ? saida.getPrimeiroVencimento()
                : saida.getDataInicial();
        Cartao cartaoPrincipal = cartao == null
                ? null
                : cartao.getCartaoPrincipal() == null ? cartao : cartao.getCartaoPrincipal();

        for (int index = 0; index < saida.getQuantidadeParcelas(); index++) {
            LocalDate vencimento = dataBase.plusMonths(index);
            Fatura fatura = null;
            if (cartaoPrincipal != null) {
                int competencia = vencimento.getYear() * 100 + vencimento.getMonthValue();
                fatura = faturaRepository.buscarPorCartaoECompetencia(cartaoPrincipal.getId(), competencia)
                        .orElse(null);
                if (fatura == null) {
                    fatura = faturaRepository.save(Fatura.builder()
                            .cartaoPrincipal(cartaoPrincipal)
                            .competencia(competencia)
                            .vencimento(vencimento)
                            .build());
                }
                vencimento = fatura.getVencimento();
            }
            SaidaParcela parcela = SaidaParcela.builder()
                    .saida(saida)
                    .numero(index + 1)
                    .valor(saida.getValorPorParcela())
                    .vencimento(vencimento)
                    .build();
            saida.getParcelas().add(parcela);
            if (fatura != null) {
                fatura.adicionarParcela(parcela);
            }
        }
    }

    private boolean cartaoDisponivel(Cartao cartao) {
        return cartao.isAtivo()
                && (cartao.getCartaoPrincipal() == null || cartao.getCartaoPrincipal().isAtivo());
    }

    private boolean possuiResponsabilidadeAnterior(ExistingReferences references, UUID pessoaId) {
        return references != null && references.pessoaIds().contains(pessoaId);
    }

    private void removerFaturasVazias(Set<Fatura> faturas) {
        faturas.stream()
                .filter(fatura -> fatura.getParcelas().isEmpty())
                .forEach(faturaRepository::delete);
    }

    private record ValidatedReferences(
            Cartao cartao, Categoria categoria, Subcategoria subcategoria, List<Pessoa> partes, Usuario usuario) {
    }

    private record ExistingReferences(
            UUID cartaoId, UUID categoriaId, UUID subcategoriaId, Set<UUID> pessoaIds) {
    }
}
