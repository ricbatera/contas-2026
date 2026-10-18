package br.com.rdrtech.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.categoria.dto.CategoriaResponse;
import br.com.rdrtech.entrada.EntradaRepository;
import br.com.rdrtech.exception.CategoriaDuplicadaException;
import br.com.rdrtech.exception.CategoriaNaoEncontradaException;
import br.com.rdrtech.saida.SaidaRepository;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private SubcategoriaRepository subcategoriaRepository;
    @Mock
    private SaidaRepository saidaRepository;
    @Mock
    private EntradaRepository entradaRepository;

    private CategoriaService service;

    @BeforeEach
    void setUp() {
        service = new CategoriaService(categoriaRepository, subcategoriaRepository, saidaRepository, entradaRepository);
    }

    @Test
    void criar_deveCriarCategoriaAtiva() {
        when(categoriaRepository.existsByTipoAndNomeIgnoreCase(TipoCategoria.DESPESA, "Moradia"))
                .thenReturn(false);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> {
            Categoria categoria = invocation.getArgument(0);
            categoria.setId(UUID.randomUUID());
            return categoria;
        });

        CategoriaResponse response = service.criar(new CategoriaRequest(" Moradia ", TipoCategoria.DESPESA, null));

        assertThat(response.nome()).isEqualTo("Moradia");
        assertThat(response.tipo()).isEqualTo(TipoCategoria.DESPESA);
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void criar_deveRejeitarCategoriaDuplicadaDoMesmoTipo() {
        when(categoriaRepository.existsByTipoAndNomeIgnoreCase(TipoCategoria.DESPESA, "Moradia"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criar(new CategoriaRequest("Moradia", TipoCategoria.DESPESA, true)))
                .isInstanceOf(CategoriaDuplicadaException.class);
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    void atualizar_deveAlterarDados() {
        UUID id = UUID.randomUUID();
        Categoria categoria = Categoria.builder().nome("Casa").tipo(TipoCategoria.DESPESA)
                .ativo(true).subcategorias(new ArrayList<>()).build();
        categoria.setId(id);
        when(categoriaRepository.buscarPorId(id)).thenReturn(Optional.of(categoria));
        when(categoriaRepository.existsByTipoAndNomeIgnoreCaseAndIdNot(
                TipoCategoria.RECEITA, "Salário", id)).thenReturn(false);

        CategoriaResponse response = service.atualizar(id, new CategoriaRequest("Salário", TipoCategoria.RECEITA, false));

        assertThat(response.nome()).isEqualTo("Salário");
        assertThat(response.tipo()).isEqualTo(TipoCategoria.RECEITA);
        assertThat(response.ativo()).isFalse();
    }

    @Test
    void excluir_deveRemoverCategoriaEFilhosEmCascata() {
        UUID id = UUID.randomUUID();
        Categoria categoria = Categoria.builder().nome("Moradia").tipo(TipoCategoria.DESPESA)
                .ativo(true).subcategorias(new ArrayList<>()).build();
        categoria.setId(id);
        Subcategoria subcategoria = Subcategoria.builder()
                .nome("Aluguel").ativo(true).tipo(TipoCategoria.DESPESA).categoria(categoria).build();
        categoria.getSubcategorias().add(subcategoria);
        when(categoriaRepository.buscarPorId(id)).thenReturn(Optional.of(categoria));

        service.excluir(id);

        assertThat(categoria.getSubcategorias()).isEmpty();
        verify(categoriaRepository).delete(categoria);
    }

    @Test
    void buscarPorId_deveFalharQuandoCategoriaNaoExiste() {
        UUID id = UUID.randomUUID();
        when(categoriaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(id))
                .isInstanceOf(CategoriaNaoEncontradaException.class);
    }
}
