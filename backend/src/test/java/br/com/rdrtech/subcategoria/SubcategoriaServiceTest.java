package br.com.rdrtech.subcategoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.exception.SubcategoriaDuplicadaException;
import br.com.rdrtech.exception.SubcategoriaNaoEncontradaException;
import br.com.rdrtech.saida.SaidaRepository;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubcategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private SubcategoriaRepository subcategoriaRepository;
    @Mock
    private SaidaRepository saidaRepository;

    private SubcategoriaService service;

    @BeforeEach
    void setUp() {
        service = new SubcategoriaService(categoriaRepository, subcategoriaRepository, saidaRepository);
    }

    @Test
    void criar_deveAssociarSubcategoriaACategoria() {
        UUID categoryId = UUID.randomUUID();
        Categoria categoria = Categoria.builder().nome("Moradia").tipo(TipoCategoria.DESPESA).ativo(true).build();
        categoria.setId(categoryId);
        when(categoriaRepository.buscarPorId(categoryId)).thenReturn(Optional.of(categoria));
        when(subcategoriaRepository.existsByCategoria_IdAndNomeIgnoreCase(categoryId, "Aluguel")).thenReturn(false);
        when(subcategoriaRepository.save(any(Subcategoria.class))).thenAnswer(invocation -> {
            Subcategoria result = invocation.getArgument(0);
            result.setId(UUID.randomUUID());
            return result;
        });

        var response = service.criar(categoryId, new SubcategoriaCreateRequest("Aluguel", null));

        assertThat(response.nome()).isEqualTo("Aluguel");
        assertThat(response.categoriaId()).isEqualTo(categoryId);
        assertThat(response.ativo()).isTrue();
        assertThat(response.padrao()).isFalse();
    }

    @Test
    void criar_deveDefinirSubcategoriaComoPadraoDoTipo() {
        UUID categoryId = UUID.randomUUID();
        Categoria categoria = Categoria.builder().nome("Moradia").tipo(TipoCategoria.DESPESA).ativo(true).build();
        categoria.setId(categoryId);
        when(categoriaRepository.buscarPorId(categoryId)).thenReturn(Optional.of(categoria));
        when(subcategoriaRepository.existsByCategoria_IdAndNomeIgnoreCase(categoryId, "Aluguel")).thenReturn(false);
        when(subcategoriaRepository.listarPadroesPorTipo(TipoCategoria.DESPESA)).thenReturn(java.util.List.of());
        when(subcategoriaRepository.save(any(Subcategoria.class))).thenAnswer(invocation -> {
            Subcategoria result = invocation.getArgument(0);
            result.setId(UUID.randomUUID());
            return result;
        });

        var response = service.criar(categoryId, new SubcategoriaCreateRequest("Aluguel", true, true));

        assertThat(response.padrao()).isTrue();
        verify(subcategoriaRepository).listarPadroesPorTipo(TipoCategoria.DESPESA);
    }

    @Test
    void criar_deveLimparPadraoAnteriorDoMesmoTipo() {
        UUID categoryId = UUID.randomUUID();
        Categoria categoria = Categoria.builder().nome("Alimentação").tipo(TipoCategoria.DESPESA).ativo(true).build();
        categoria.setId(categoryId);
        Subcategoria anterior = Subcategoria.builder()
                .nome("Restaurante").ativo(true).tipo(TipoCategoria.DESPESA).padrao(true).categoria(categoria).build();
        when(categoriaRepository.buscarPorId(categoryId)).thenReturn(Optional.of(categoria));
        when(subcategoriaRepository.existsByCategoria_IdAndNomeIgnoreCase(categoryId, "Supermercado")).thenReturn(false);
        when(subcategoriaRepository.listarPadroesPorTipo(TipoCategoria.DESPESA))
                .thenReturn(java.util.List.of(anterior));
        when(subcategoriaRepository.save(any(Subcategoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.criar(categoryId, new SubcategoriaCreateRequest("Supermercado", true, true));

        assertThat(anterior.isPadrao()).isFalse();
        verify(subcategoriaRepository, times(1)).save(any(Subcategoria.class));
    }

    @Test
    void criar_deveRejeitarPadraoEmCategoriaInativa() {
        UUID categoryId = UUID.randomUUID();
        Categoria categoria = Categoria.builder().nome("Moradia").tipo(TipoCategoria.DESPESA).ativo(false).build();
        categoria.setId(categoryId);
        when(categoriaRepository.buscarPorId(categoryId)).thenReturn(Optional.of(categoria));

        assertThatThrownBy(() -> service.criar(categoryId, new SubcategoriaCreateRequest("Aluguel", true, true)))
                .isInstanceOf(br.com.rdrtech.exception.PadraoCategoriaInativoException.class);
    }

    @Test
    void criar_deveRejeitarNomeDuplicadoNaCategoria() {
        UUID categoryId = UUID.randomUUID();
        Categoria categoria = Categoria.builder().nome("Moradia").tipo(TipoCategoria.DESPESA).build();
        categoria.setId(categoryId);
        when(categoriaRepository.buscarPorId(categoryId)).thenReturn(Optional.of(categoria));
        when(subcategoriaRepository.existsByCategoria_IdAndNomeIgnoreCase(categoryId, "Aluguel")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(categoryId, new SubcategoriaCreateRequest("Aluguel", true)))
                .isInstanceOf(SubcategoriaDuplicadaException.class);
        verify(subcategoriaRepository, never()).save(any());
    }

    @Test
    void excluir_deveFalharQuandoSubcategoriaNaoExiste() {
        UUID id = UUID.randomUUID();
        when(subcategoriaRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(id))
                .isInstanceOf(SubcategoriaNaoEncontradaException.class);
        verify(subcategoriaRepository, never()).delete(any());
    }
}
