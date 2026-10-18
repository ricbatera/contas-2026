package br.com.rdrtech.contabancaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import br.com.rdrtech.contabancaria.dto.ContaBancariaRequest;
import br.com.rdrtech.contabancaria.dto.ContaBancariaResponse;
import br.com.rdrtech.entrada.EntradaRepository;
import br.com.rdrtech.exception.ContaBancariaDuplicadaException;
import br.com.rdrtech.exception.ContaBancariaNaoEncontradaException;
import br.com.rdrtech.exception.LimiteContasExcedidoException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContaBancariaServiceTest {

    @Mock
    private ContaBancariaRepository contaBancariaRepository;
    @Mock
    private EntradaRepository entradaRepository;

    private ContaBancariaService contaBancariaService;

    @BeforeEach
    void setUp() {
        contaBancariaService = new ContaBancariaService(contaBancariaRepository, entradaRepository);
    }

    private ContaBancariaRequest requestValido() {
        return new ContaBancariaRequest(
                "Conta Corrente Itaú", "Itaú", TipoConta.CORRENTE, "1234", "56789-0",
                BigDecimal.valueOf(1000), "#EC7000", true);
    }

    private ContaBancaria contaExistente(UUID id) {
        ContaBancaria conta = ContaBancaria.builder()
                .nome("Conta Corrente Itaú").banco("Itaú").tipoConta(TipoConta.CORRENTE)
                .agencia("1234").numeroConta("56789-0").saldoInicial(BigDecimal.valueOf(1000))
                .cor("#EC7000").ativo(true).build();
        conta.setId(id);
        return conta;
    }

    @Test
    void criar_deveSalvarContaBancaria_quandoDadosValidos() {
        ContaBancariaRequest request = requestValido();
        when(contaBancariaRepository.count()).thenReturn(0L);
        when(contaBancariaRepository.existsByNomeIgnoreCase(request.nome())).thenReturn(false);
        when(contaBancariaRepository.save(any(ContaBancaria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContaBancariaResponse response = contaBancariaService.criar(request);

        ArgumentCaptor<ContaBancaria> captor = ArgumentCaptor.forClass(ContaBancaria.class);
        verify(contaBancariaRepository).save(captor.capture());
        ContaBancaria salvo = captor.getValue();
        assertThat(salvo.getNome()).isEqualTo(request.nome());
        assertThat(salvo.getBanco()).isEqualTo(request.banco());
        assertThat(salvo.getTipoConta()).isEqualTo(request.tipoConta());
        assertThat(salvo.getAgencia()).isEqualTo(request.agencia());
        assertThat(salvo.getNumeroConta()).isEqualTo(request.numeroConta());
        assertThat(salvo.getSaldoInicial()).isEqualTo(request.saldoInicial());
        assertThat(salvo.getCor()).isEqualTo(request.cor());
        assertThat(salvo.isAtivo()).isEqualTo(request.ativo());
        assertThat(response.nome()).isEqualTo(request.nome());
    }

    @Test
    void criar_deveLancarLimiteContasExcedidoException_quandoJaTem20Contas() {
        when(contaBancariaRepository.count()).thenReturn(20L);

        assertThatThrownBy(() -> contaBancariaService.criar(requestValido()))
                .isInstanceOf(LimiteContasExcedidoException.class);
        verify(contaBancariaRepository, never()).save(any());
    }

    @Test
    void criar_deveLancarContaBancariaDuplicadaException_quandoNomeJaExiste() {
        ContaBancariaRequest request = requestValido();
        when(contaBancariaRepository.count()).thenReturn(0L);
        when(contaBancariaRepository.existsByNomeIgnoreCase(request.nome())).thenReturn(true);

        assertThatThrownBy(() -> contaBancariaService.criar(request))
                .isInstanceOf(ContaBancariaDuplicadaException.class);
        verify(contaBancariaRepository, never()).save(any());
    }

    @Test
    void buscarPorId_deveLancarContaBancariaNaoEncontradaException_quandoIdInexistente() {
        UUID id = UUID.randomUUID();
        when(contaBancariaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaBancariaService.buscarPorId(id))
                .isInstanceOf(ContaBancariaNaoEncontradaException.class);
    }

    @Test
    void atualizar_deveAtualizarCampos_quandoContaExiste() {
        UUID id = UUID.randomUUID();
        ContaBancaria existente = contaExistente(id);
        when(contaBancariaRepository.buscarPorId(id)).thenReturn(Optional.of(existente));
        when(contaBancariaRepository.existsByNomeIgnoreCaseAndIdNot(eq("Novo Nome"), eq(id))).thenReturn(false);

        ContaBancariaRequest request = new ContaBancariaRequest(
                "Novo Nome", "Bradesco", TipoConta.POUPANCA, "4321", "0-98765",
                BigDecimal.valueOf(500), "#FF5733", false);
        ContaBancariaResponse response = contaBancariaService.atualizar(id, request);

        assertThat(existente.getNome()).isEqualTo("Novo Nome");
        assertThat(existente.getBanco()).isEqualTo("Bradesco");
        assertThat(existente.getTipoConta()).isEqualTo(TipoConta.POUPANCA);
        assertThat(existente.getAgencia()).isEqualTo("4321");
        assertThat(existente.getNumeroConta()).isEqualTo("0-98765");
        assertThat(existente.getSaldoInicial()).isEqualTo(BigDecimal.valueOf(500));
        assertThat(existente.getCor()).isEqualTo("#FF5733");
        assertThat(existente.isAtivo()).isFalse();
        assertThat(response.nome()).isEqualTo("Novo Nome");
    }

    @Test
    void atualizar_deveLancarContaBancariaNaoEncontradaException_quandoIdInexistente() {
        UUID id = UUID.randomUUID();
        when(contaBancariaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaBancariaService.atualizar(id, requestValido()))
                .isInstanceOf(ContaBancariaNaoEncontradaException.class);
    }

    @Test
    void atualizar_deveLancarContaBancariaDuplicadaException_quandoNovoNomeConflitaComOutraConta() {
        UUID id = UUID.randomUUID();
        ContaBancaria existente = contaExistente(id);
        when(contaBancariaRepository.buscarPorId(id)).thenReturn(Optional.of(existente));
        when(contaBancariaRepository.existsByNomeIgnoreCaseAndIdNot("Novo Nome", id)).thenReturn(true);

        ContaBancariaRequest request = new ContaBancariaRequest(
                "Novo Nome", "Bradesco", TipoConta.POUPANCA, null, null, BigDecimal.ZERO, null, true);

        assertThatThrownBy(() -> contaBancariaService.atualizar(id, request))
                .isInstanceOf(ContaBancariaDuplicadaException.class);
    }

    @Test
    void excluir_deveRemoverContaBancaria_quandoExiste() {
        UUID id = UUID.randomUUID();
        ContaBancaria existente = contaExistente(id);
        when(contaBancariaRepository.buscarPorId(id)).thenReturn(Optional.of(existente));

        contaBancariaService.excluir(id);

        verify(contaBancariaRepository).delete(existente);
    }

    @Test
    void excluir_deveLancarContaBancariaNaoEncontradaException_quandoIdInexistente() {
        UUID id = UUID.randomUUID();
        when(contaBancariaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaBancariaService.excluir(id))
                .isInstanceOf(ContaBancariaNaoEncontradaException.class);
        verify(contaBancariaRepository, never()).delete(any());
    }
}
