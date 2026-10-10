package br.com.rdrtech.categoria;

import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.categoria.dto.CategoriaResponse;
import br.com.rdrtech.exception.CategoriaDuplicadaException;
import br.com.rdrtech.exception.CategoriaNaoEncontradaException;
import br.com.rdrtech.exception.RegistroVinculadoException;
import br.com.rdrtech.saida.SaidaRepository;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final SaidaRepository saidaRepository;

    public List<CategoriaResponse> listar() {
        return categoriaRepository.listarComSubcategorias().stream()
                .sorted(Comparator.comparing(CategoriaMapper::isPadrao).reversed()
                        .thenComparing(Categoria::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(CategoriaMapper::toResponse)
                .toList();
    }

    public CategoriaResponse buscarPorId(UUID id) {
        return categoriaRepository.buscarPorId(id)
                .map(CategoriaMapper::toResponse)
                .orElseThrow(CategoriaNaoEncontradaException::new);
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest request) {
        validarDuplicidade(request.tipo(), request.nome(), null);
        Categoria categoria = Categoria.builder()
                .nome(request.nome().trim())
                .tipo(request.tipo())
                .ativo(request.ativo() == null || request.ativo())
                .build();
        return CategoriaMapper.toResponse(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse atualizar(UUID id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.buscarPorId(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);
        if (categoria.getTipo() != request.tipo() && saidaRepository.existsByCategoria_Id(id)) {
            throw new RegistroVinculadoException("a categoria");
        }
        validarDuplicidade(request.tipo(), request.nome(), id);
        boolean tipoAlterado = categoria.getTipo() != request.tipo();
        categoria.setNome(request.nome().trim());
        categoria.setTipo(request.tipo());
        if (request.ativo() != null) {
            categoria.setAtivo(request.ativo());
        }
        if (tipoAlterado) {
            boolean temPadrao = categoria.getSubcategorias().stream().anyMatch(Subcategoria::isPadrao);
            if (temPadrao) {
                subcategoriaRepository.listarPadroesPorTipo(request.tipo()).stream()
                        .filter(subcategoria -> !categoria.getSubcategorias().contains(subcategoria))
                        .forEach(subcategoria -> subcategoria.setPadrao(false));
                subcategoriaRepository.flush();
            }
            categoria.getSubcategorias().forEach(subcategoria -> subcategoria.setTipo(request.tipo()));
        }
        if (!categoria.isAtivo()) {
            categoria.getSubcategorias().forEach(subcategoria -> subcategoria.setPadrao(false));
        }
        return CategoriaMapper.toResponse(categoria);
    }

    @Transactional
    public void excluir(UUID id) {
        Categoria categoria = categoriaRepository.buscarPorId(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);
        if (saidaRepository.existsByCategoria_Id(id)) {
            throw new RegistroVinculadoException("a categoria");
        }
        List<Subcategoria> subcategorias = categoria.getSubcategorias();
        subcategorias.clear();
        categoriaRepository.delete(categoria);
    }

    private void validarDuplicidade(TipoCategoria tipo, String nome, UUID idAtual) {
        boolean duplicada = idAtual == null
                ? categoriaRepository.existsByTipoAndNomeIgnoreCase(tipo, nome.trim())
                : categoriaRepository.existsByTipoAndNomeIgnoreCaseAndIdNot(tipo, nome.trim(), idAtual);
        if (duplicada) {
            throw new CategoriaDuplicadaException();
        }
    }
}
