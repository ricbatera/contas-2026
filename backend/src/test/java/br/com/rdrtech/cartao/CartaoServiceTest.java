package br.com.rdrtech.cartao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.cartao.dto.CartaoResponse;
import br.com.rdrtech.exception.CartaoDuplicadoException;
import br.com.rdrtech.exception.CartaoNaoEncontradoException;
import br.com.rdrtech.exception.LimiteCartoesExcedidoException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartaoServiceTest {

    @Mock
    private CartaoRepository cartaoRepository;

    private CartaoService cartaoService;

    @BeforeEach
    void setUp() {
        cartaoService = new CartaoService(cartaoRepository);
    }

    private CartaoRequest requestValido() {
        return new CartaoRequest("Nubank Roxinho", "Nubank", Bandeira.MASTERCARD, "1234", "#8A05BE");
    }

    private Cartao cartaoExistente(UUID id) {
        Cartao cartao = Cartao.builder()
                .nome("Nubank Roxinho").banco("Nubank").bandeira(Bandeira.MASTERCARD)
                .ultimosDigitos("1234").cor("#8A05BE").build();
        cartao.setId(id);
        return cartao;
    }

    @Test
    void criar_deveSalvarCartao_quandoDadosValidos() {
        CartaoRequest request = requestValido();
        when(cartaoRepository.count()).thenReturn(0L);
        when(cartaoRepository.existsByBandeiraAndUltimosDigitos(request.bandeira(), request.ultimosDigitos()))
                .thenReturn(false);
        when(cartaoRepository.save(any(Cartao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartaoResponse response = cartaoService.criar(request);

        ArgumentCaptor<Cartao> captor = ArgumentCaptor.forClass(Cartao.class);
        verify(cartaoRepository).save(captor.capture());
        Cartao salvo = captor.getValue();
        assertThat(salvo.getNome()).isEqualTo(request.nome());
        assertThat(salvo.getBanco()).isEqualTo(request.banco());
        assertThat(salvo.getBandeira()).isEqualTo(request.bandeira());
        assertThat(salvo.getUltimosDigitos()).isEqualTo(request.ultimosDigitos());
        assertThat(salvo.getCor()).isEqualTo(request.cor());
        assertThat(response.nome()).isEqualTo(request.nome());
    }

    @Test
    void criar_deveLancarLimiteCartoesExcedidoException_quandoJaTem20Cartoes() {
        when(cartaoRepository.count()).thenReturn(20L);

        assertThatThrownBy(() -> cartaoService.criar(requestValido()))
                .isInstanceOf(LimiteCartoesExcedidoException.class);
        verify(cartaoRepository, never()).save(any());
    }

    @Test
    void criar_deveLancarCartaoDuplicadoException_quandoBandeiraEDigitosJaExistem() {
        CartaoRequest request = requestValido();
        when(cartaoRepository.count()).thenReturn(0L);
        when(cartaoRepository.existsByBandeiraAndUltimosDigitos(request.bandeira(), request.ultimosDigitos()))
                .thenReturn(true);

        assertThatThrownBy(() -> cartaoService.criar(request)).isInstanceOf(CartaoDuplicadoException.class);
        verify(cartaoRepository, never()).save(any());
    }

    @Test
    void buscarPorId_deveLancarCartaoNaoEncontradoException_quandoIdInexistente() {
        UUID id = UUID.randomUUID();
        when(cartaoRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartaoService.buscarPorId(id)).isInstanceOf(CartaoNaoEncontradoException.class);
    }

    @Test
    void atualizar_deveAtualizarCampos_quandoCartaoExiste() {
        UUID id = UUID.randomUUID();
        Cartao existente = cartaoExistente(id);
        when(cartaoRepository.buscarPorId(id)).thenReturn(Optional.of(existente));
        when(cartaoRepository.existsByBandeiraAndUltimosDigitosAndIdNot(eq(Bandeira.VISA), eq("9999"), eq(id)))
                .thenReturn(false);

        CartaoRequest request = new CartaoRequest("Novo Nome", "Itaú", Bandeira.VISA, "9999", "#FF5733");
        CartaoResponse response = cartaoService.atualizar(id, request);

        assertThat(existente.getNome()).isEqualTo("Novo Nome");
        assertThat(existente.getBanco()).isEqualTo("Itaú");
        assertThat(existente.getBandeira()).isEqualTo(Bandeira.VISA);
        assertThat(existente.getUltimosDigitos()).isEqualTo("9999");
        assertThat(existente.getCor()).isEqualTo("#FF5733");
        assertThat(response.nome()).isEqualTo("Novo Nome");
    }

    @Test
    void atualizar_deveLancarCartaoNaoEncontradoException_quandoIdInexistente() {
        UUID id = UUID.randomUUID();
        when(cartaoRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartaoService.atualizar(id, requestValido()))
                .isInstanceOf(CartaoNaoEncontradoException.class);
    }

    @Test
    void atualizar_deveLancarCartaoDuplicadoException_quandoNovaBandeiraEDigitosConflitamComOutroCartao() {
        UUID id = UUID.randomUUID();
        Cartao existente = cartaoExistente(id);
        when(cartaoRepository.buscarPorId(id)).thenReturn(Optional.of(existente));
        when(cartaoRepository.existsByBandeiraAndUltimosDigitosAndIdNot(Bandeira.VISA, "9999", id))
                .thenReturn(true);

        CartaoRequest request = new CartaoRequest("Novo Nome", "Itaú", Bandeira.VISA, "9999", null);

        assertThatThrownBy(() -> cartaoService.atualizar(id, request)).isInstanceOf(CartaoDuplicadoException.class);
    }

    @Test
    void excluir_deveRemoverCartao_quandoExiste() {
        UUID id = UUID.randomUUID();
        Cartao existente = cartaoExistente(id);
        when(cartaoRepository.buscarPorId(id)).thenReturn(Optional.of(existente));

        cartaoService.excluir(id);

        verify(cartaoRepository).delete(existente);
    }

    @Test
    void excluir_deveLancarCartaoNaoEncontradoException_quandoIdInexistente() {
        UUID id = UUID.randomUUID();
        when(cartaoRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartaoService.excluir(id)).isInstanceOf(CartaoNaoEncontradoException.class);
        verify(cartaoRepository, never()).delete(any());
    }
}
