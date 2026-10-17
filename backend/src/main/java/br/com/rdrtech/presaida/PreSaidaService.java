package br.com.rdrtech.presaida;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.cartao.CartaoRepository;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.exception.BusinessException;
import br.com.rdrtech.exception.CategoriaNaoEncontradaException;
import br.com.rdrtech.exception.PreSaidaInvalidaException;
import br.com.rdrtech.exception.PreSaidaNaoEncontradaException;
import br.com.rdrtech.exception.PreSaidaStatusInvalidoException;
import br.com.rdrtech.exception.SubcategoriaNaoEncontradaException;
import br.com.rdrtech.presaida.dto.AprovacaoLoteResponse;
import br.com.rdrtech.presaida.dto.PreSaidaRequest;
import br.com.rdrtech.presaida.dto.PreSaidaResponse;
import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import br.com.rdrtech.saida.SaidaRepository;
import br.com.rdrtech.saida.SaidaService;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreSaidaService {

    private final PreSaidaRepository preSaidaRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final SaidaRepository saidaRepository;
    private final SaidaService saidaService;
    private final PlatformTransactionManager transactionManager;

    public List<PreSaidaResponse> listar(StatusPreSaida status, UUID importacaoId) {
        List<PreSaida> preSaidas;
        if (importacaoId != null) {
            preSaidas = preSaidaRepository.listarPorImportacao(importacaoId).stream()
                    .filter(item -> status == null || item.getStatus() == status)
                    .toList();
        } else if (status != null) {
            preSaidas = preSaidaRepository.listarPorStatus(status);
        } else {
            preSaidas = preSaidaRepository.listar();
        }
        return preSaidas.stream().map(PreSaidaMapper::toResponse).toList();
    }

    public PreSaidaResponse buscarPorId(UUID id) {
        return PreSaidaMapper.toResponse(buscarEntidade(id));
    }

    @Transactional
    public PreSaidaResponse atualizar(UUID id, PreSaidaRequest request) {
        PreSaida preSaida = buscarPendente(id);
        if (request.numeroParcela() > request.quantidadeParcelas()) {
            throw new PreSaidaInvalidaException("A parcela atual não pode ser maior que a quantidade de parcelas.");
        }
        Cartao cartao = null;
        if (request.cartaoId() != null) {
            cartao = cartaoRepository.buscarPorId(request.cartaoId())
                    .filter(item -> pertenceAFatura(item, preSaida))
                    .orElseThrow(() -> new PreSaidaInvalidaException(
                            "Selecione o cartão principal da fatura ou um de seus adicionais."));
        }
        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = categoriaRepository.buscarPorId(request.categoriaId())
                    .orElseThrow(CategoriaNaoEncontradaException::new);
        }
        Subcategoria subcategoria = null;
        if (request.subcategoriaId() != null) {
            UUID categoriaId = categoria == null ? null : categoria.getId();
            subcategoria = subcategoriaRepository.buscarPorId(request.subcategoriaId())
                    .filter(item -> item.getCategoria().getId().equals(categoriaId))
                    .orElseThrow(SubcategoriaNaoEncontradaException::new);
        }

        // A parcela lida está na fatura da importação; mudar o número recalcula a primeira parcela.
        preSaida.setDescricao(request.descricao().trim());
        preSaida.setCartao(cartao);
        preSaida.setCategoria(categoria);
        preSaida.setSubcategoria(subcategoria);
        preSaida.setValorPorParcela(request.valorPorParcela());
        preSaida.setPrimeiroVencimento(preSaida.getPrimeiroVencimento()
                .plusMonths(preSaida.getNumeroParcela() - 1L)
                .minusMonths(request.numeroParcela() - 1L));
        preSaida.setNumeroParcela(request.numeroParcela());
        preSaida.setQuantidadeParcelas(request.quantidadeParcelas());
        return PreSaidaMapper.toResponse(preSaida);
    }

    /** Cria a saída (com todas as parcelas, inclusive as anteriores à fatura lida) e marca como aprovada. */
    @Transactional
    public PreSaidaResponse aprovar(UUID id) {
        PreSaida preSaida = buscarPendente(id);
        if (preSaida.getCartao() == null || preSaida.getCategoria() == null || preSaida.getSubcategoria() == null) {
            throw new PreSaidaInvalidaException("Informe cartão, categoria e subcategoria antes de aprovar.");
        }
        var saida = saidaService.criar(new SaidaRequest(
                preSaida.getDescricao(),
                preSaida.getDescricaoFatura(),
                preSaida.getValorPorParcela(),
                preSaida.getQuantidadeParcelas(),
                FormaPagamento.CARTAO_CREDITO,
                null,
                preSaida.getPrimeiroVencimento(),
                preSaida.getCartao().getId(),
                preSaida.getCategoria().getId(),
                preSaida.getSubcategoria().getId(),
                ModoResponsabilidade.USUARIO,
                null,
                List.of()));
        preSaida.setSaida(saidaRepository.getReferenceById(saida.id()));
        preSaida.setStatus(StatusPreSaida.APROVADA);
        return PreSaidaMapper.toResponse(preSaida);
    }

    /** Cada aprovação roda em transação própria, para uma pré-saída inválida não desfazer as outras. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AprovacaoLoteResponse aprovarLote(List<UUID> ids) {
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        List<PreSaidaResponse> aprovadas = new ArrayList<>();
        List<AprovacaoLoteResponse.Falha> falhas = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            try {
                aprovadas.add(transacao.execute(status -> aprovar(id)));
            } catch (BusinessException e) {
                falhas.add(new AprovacaoLoteResponse.Falha(id, e.getMessage()));
            }
        }
        return new AprovacaoLoteResponse(aprovadas, falhas);
    }

    @Transactional
    public PreSaidaResponse rejeitar(UUID id) {
        PreSaida preSaida = buscarPendente(id);
        preSaida.setStatus(StatusPreSaida.REJEITADA);
        return PreSaidaMapper.toResponse(preSaida);
    }

    /** Devolve uma pré-saída rejeitada para a fila de aprovação. */
    @Transactional
    public PreSaidaResponse reabrir(UUID id) {
        PreSaida preSaida = buscarEntidade(id);
        if (preSaida.getStatus() != StatusPreSaida.REJEITADA) {
            throw new PreSaidaStatusInvalidoException("Só pré-saídas rejeitadas podem ser reabertas.");
        }
        preSaida.setStatus(StatusPreSaida.PENDENTE);
        return PreSaidaMapper.toResponse(preSaida);
    }

    private boolean pertenceAFatura(Cartao cartao, PreSaida preSaida) {
        Cartao principalDaFatura = preSaida.getImportacao().getCartaoPrincipal();
        Cartao principal = cartao.getCartaoPrincipal() == null ? cartao : cartao.getCartaoPrincipal();
        return principalDaFatura == null || principal.getId().equals(principalDaFatura.getId());
    }

    private PreSaida buscarPendente(UUID id) {
        PreSaida preSaida = buscarEntidade(id);
        if (preSaida.getStatus() != StatusPreSaida.PENDENTE) {
            throw new PreSaidaStatusInvalidoException("A pré-saída já foi " + (preSaida.getStatus() == StatusPreSaida.APROVADA
                    ? "aprovada" : "rejeitada") + ".");
        }
        return preSaida;
    }

    private PreSaida buscarEntidade(UUID id) {
        return preSaidaRepository.buscarPorId(id).orElseThrow(PreSaidaNaoEncontradaException::new);
    }
}
