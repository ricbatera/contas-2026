package br.com.rdrtech.cartao;

import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.cartao.dto.CartaoResponse;
import br.com.rdrtech.exception.CartaoDuplicadoException;
import br.com.rdrtech.exception.CartaoNaoEncontradoException;
import br.com.rdrtech.exception.LimiteCartoesExcedidoException;
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

    public List<CartaoResponse> listar() {
        return cartaoRepository.findAll().stream().map(CartaoMapper::toResponse).toList();
    }

    public CartaoResponse buscarPorId(UUID id) {
        return cartaoRepository.buscarPorId(id)
                .map(CartaoMapper::toResponse)
                .orElseThrow(CartaoNaoEncontradoException::new);
    }

    @Transactional
    public CartaoResponse criar(CartaoRequest request) {
        if (cartaoRepository.count() >= LIMITE_CARTOES) {
            throw new LimiteCartoesExcedidoException();
        }
        validarDuplicidade(request.bandeira(), request.ultimosDigitos(), null);

        Cartao cartao = Cartao.builder()
                .nome(request.nome())
                .banco(request.banco())
                .bandeira(request.bandeira())
                .ultimosDigitos(request.ultimosDigitos())
                .cor(request.cor())
                .build();
        return CartaoMapper.toResponse(cartaoRepository.save(cartao));
    }

    @Transactional
    public CartaoResponse atualizar(UUID id, CartaoRequest request) {
        Cartao cartao = cartaoRepository.buscarPorId(id).orElseThrow(CartaoNaoEncontradoException::new);
        validarDuplicidade(request.bandeira(), request.ultimosDigitos(), id);

        cartao.setNome(request.nome());
        cartao.setBanco(request.banco());
        cartao.setBandeira(request.bandeira());
        cartao.setUltimosDigitos(request.ultimosDigitos());
        cartao.setCor(request.cor());
        return CartaoMapper.toResponse(cartao);
    }

    @Transactional
    public void excluir(UUID id) {
        Cartao cartao = cartaoRepository.buscarPorId(id).orElseThrow(CartaoNaoEncontradoException::new);
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
}
