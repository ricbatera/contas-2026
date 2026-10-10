package br.com.rdrtech.pessoa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.rdrtech.exception.PessoaNaoEncontradaException;
import br.com.rdrtech.pessoa.dto.PessoaRequest;
import br.com.rdrtech.pessoa.dto.PessoaResponse;
import br.com.rdrtech.saida.SaidaRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PessoaServiceTest {

    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private SaidaRepository saidaRepository;

    private PessoaService pessoaService;

    @BeforeEach
    void setUp() {
        pessoaService = new PessoaService(pessoaRepository, saidaRepository);
    }

    @Test
    void criar_deveCriarPessoaAtiva_quandoStatusNaoInformado() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(invocation -> {
            Pessoa pessoa = invocation.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });

        PessoaResponse response = pessoaService.criar(new PessoaRequest("  Maria Silva  ", null));

        assertThat(response.nome()).isEqualTo("Maria Silva");
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void atualizar_deveAtualizarNomeEStatus() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().nome("Maria").ativo(true).build();
        pessoa.setId(id);
        when(pessoaRepository.buscarPorId(id)).thenReturn(Optional.of(pessoa));

        PessoaResponse response = pessoaService.atualizar(id, new PessoaRequest("Ana", false));

        assertThat(response.nome()).isEqualTo("Ana");
        assertThat(response.ativo()).isFalse();
    }

    @Test
    void buscarPorId_deveLancarExcecao_quandoNaoEncontrada() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.buscarPorId(id))
                .isInstanceOf(PessoaNaoEncontradaException.class);
    }

    @Test
    void excluir_deveExcluirPessoaExistente() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().nome("Maria").ativo(true).build();
        pessoa.setId(id);
        when(pessoaRepository.buscarPorId(id)).thenReturn(Optional.of(pessoa));

        pessoaService.excluir(id);

        verify(pessoaRepository).delete(pessoa);
    }

    @Test
    void excluir_deveLancarExcecao_quandoPessoaNaoEncontrada() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.excluir(id))
                .isInstanceOf(PessoaNaoEncontradaException.class);
        verify(pessoaRepository, never()).delete(any());
    }
}
