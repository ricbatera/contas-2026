package br.com.rdrtech.cartao;

import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.cartao.dto.CartaoResponse;
import br.com.rdrtech.exception.CartaoDuplicadoException;
import br.com.rdrtech.exception.CartaoInvalidoException;
import br.com.rdrtech.exception.CartaoNaoEncontradoException;
import br.com.rdrtech.exception.CartaoPrincipalEmUsoException;
import br.com.rdrtech.exception.CartaoPrincipalInvalidoException;
import br.com.rdrtech.exception.LimiteCartoesExcedidoException;
import br.com.rdrtech.exception.RegistroVinculadoException;
import br.com.rdrtech.fatura.FaturaRepository;
import br.com.rdrtech.saida.SaidaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartaoService {

    private static final int LIMITE_CARTOES = 20;

    private final CartaoRepository cartaoRepository;
    private final SaidaRepository saidaRepository;
    private final FaturaRepository faturaRepository;

    public List<CartaoResponse> listar() {
        return cartaoRepository.findAll().stream().map(CartaoMapper::toResponse).toList();
    }

    public CartaoResponse buscarPorId(UUID id) {
        return cartaoRepository.buscarPorId(id)
                .map(CartaoMapper::toResponse)
                .orElseThrow(CartaoNaoEncontradoException::new);
    }

    public List<CartaoResponse> listarPrincipais() {
        return cartaoRepository.listarPrincipais().stream()
                .filter(Cartao::isAtivo)
                .map(CartaoMapper::toResponse)
                .toList();
    }

    @Transactional
    public CartaoResponse criar(CartaoRequest request) {
        if (cartaoRepository.count() >= LIMITE_CARTOES) {
            throw new LimiteCartoesExcedidoException();
        }
        validarDuplicidade(request.bandeira(), request.ultimosDigitos(), null);
        TipoCartao tipo = tipoEfetivo(request);
        Cartao principal = resolverPrincipal(tipo, request.cartaoPrincipalId());
        validarDias(tipo, request);

        Cartao cartao = Cartao.builder()
                .nome(request.nome())
                .banco(request.banco())
                .bandeira(request.bandeira())
                .ultimosDigitos(request.ultimosDigitos())
                .cor(request.cor())
                .tipo(tipo)
                .virtual(Boolean.TRUE.equals(request.virtual()))
                .ativo(request.ativo() == null || request.ativo())
                .cartaoPrincipal(principal)
                .diaFechamento(request.diaFechamento())
                .diaVencimento(request.diaVencimento())
                .build();
        return CartaoMapper.toResponse(cartaoRepository.save(cartao));
    }

    @Transactional
    public CartaoResponse atualizar(UUID id, CartaoRequest request) {
        Cartao cartao = cartaoRepository.buscarPorId(id).orElseThrow(CartaoNaoEncontradoException::new);
        validarDuplicidade(request.bandeira(), request.ultimosDigitos(), id);
        TipoCartao tipo = tipoEfetivo(request);
        if (cartao.getTipo() == TipoCartao.PRINCIPAL
                && tipo == TipoCartao.ADICIONAL
                && cartaoRepository.existsByCartaoPrincipal_Id(id)) {
            throw new CartaoPrincipalInvalidoException();
        }
        Cartao principal = resolverPrincipal(tipo, request.cartaoPrincipalId());
        validarDias(tipo, request);

        cartao.setNome(request.nome());
        cartao.setBanco(request.banco());
        cartao.setBandeira(request.bandeira());
        cartao.setUltimosDigitos(request.ultimosDigitos());
        cartao.setCor(request.cor());
        cartao.setTipo(tipo);
        cartao.setVirtual(Boolean.TRUE.equals(request.virtual()));
        if (request.ativo() != null) {
            cartao.setAtivo(request.ativo());
        }
        cartao.setCartaoPrincipal(principal);
        cartao.setDiaFechamento(request.diaFechamento());
        cartao.setDiaVencimento(request.diaVencimento());
        return CartaoMapper.toResponse(cartao);
    }

    @Transactional
    public void excluir(UUID id) {
        Cartao cartao = cartaoRepository.buscarPorId(id).orElseThrow(CartaoNaoEncontradoException::new);
        if (cartao.getTipo() == TipoCartao.PRINCIPAL && cartaoRepository.existsByCartaoPrincipal_Id(id)) {
            throw new CartaoPrincipalEmUsoException();
        }
        if (saidaRepository.existsByCartao_Id(id) || faturaRepository.existsByCartaoPrincipal_Id(id)) {
            throw new RegistroVinculadoException("o cartão");
        }
        cartaoRepository.delete(cartao);
    }

    private void validarDuplicidade(Bandeira bandeira, String ultimosDigitos, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? cartaoRepository.existsByBandeiraAndUltimosDigitos(bandeira, ultimosDigitos)
                : cartaoRepository.existsByBandeiraAndUltimosDigitosAndIdNot(bandeira, ultimosDigitos, idAtual);
        if (duplicado) {
            throw new CartaoDuplicadoException(bandeira, ultimosDigitos);
        }
    }

    /** O adicional entra na fatura do principal, então fechamento e vencimento só existem no principal. */
    private void validarDias(TipoCartao tipo, CartaoRequest request) {
        if (tipo == TipoCartao.ADICIONAL && (request.diaFechamento() != null || request.diaVencimento() != null)) {
            throw new CartaoInvalidoException(
                    "Dias de fechamento e vencimento são do cartão principal; o adicional usa os do principal.");
        }
    }

    private TipoCartao tipoEfetivo(CartaoRequest request) {
        return request.tipo() == null ? TipoCartao.PRINCIPAL : request.tipo();
    }

    private Cartao resolverPrincipal(TipoCartao tipo, UUID principalId) {
        if (tipo == TipoCartao.PRINCIPAL) {
            if (principalId != null) {
                throw new CartaoPrincipalInvalidoException();
            }
            return null;
        }
        if (principalId == null) {
            throw new CartaoPrincipalInvalidoException();
        }
        return cartaoRepository.buscarPrincipalPorId(principalId)
                .filter(Cartao::isAtivo)
                .orElseThrow(CartaoPrincipalInvalidoException::new);
    }
}
