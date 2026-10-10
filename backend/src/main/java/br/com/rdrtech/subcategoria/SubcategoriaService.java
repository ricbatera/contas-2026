package br.com.rdrtech.subcategoria;

import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.CategoriaMapper;
import br.com.rdrtech.exception.CategoriaNaoEncontradaException;
import br.com.rdrtech.exception.PadraoCategoriaInativoException;
import br.com.rdrtech.exception.RegistroVinculadoException;
import br.com.rdrtech.exception.SubcategoriaDuplicadaException;
import br.com.rdrtech.exception.SubcategoriaNaoEncontradaException;
import br.com.rdrtech.saida.SaidaRepository;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubcategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final SaidaRepository saidaRepository;

    public List<SubcategoriaResponse> listar(UUID categoriaId) {
        buscarCategoria(categoriaId);
        return subcategoriaRepository.listarPorCategoriaId(categoriaId).stream()
                .map(CategoriaMapper::toSubcategoriaResponse)
                .toList();
    }

    public SubcategoriaResponse buscarPorId(UUID id) {
        return subcategoriaRepository.buscarPorId(id)
                .map(CategoriaMapper::toSubcategoriaResponse)
                .orElseThrow(SubcategoriaNaoEncontradaException::new);
    }

    @Transactional
    public SubcategoriaResponse criar(UUID categoriaId, SubcategoriaCreateRequest request) {
        Categoria categoria = buscarCategoria(categoriaId);
        validarDuplicidade(categoria.getId(), request.nome(), null);
        boolean padrao = Boolean.TRUE.equals(request.padrao());
        validarPadrao(categoria, Boolean.TRUE.equals(request.ativo()) || request.ativo() == null, padrao);
        if (padrao) {
            limparPadraoDoTipo(categoria.getTipo(), null);
        }
        Subcategoria subcategoria = Subcategoria.builder()
                .nome(request.nome().trim())
                .ativo(request.ativo() == null || request.ativo())
                .tipo(categoria.getTipo())
                .padrao(padrao)
                .categoria(categoria)
                .build();
        return CategoriaMapper.toSubcategoriaResponse(subcategoriaRepository.save(subcategoria));
    }

    @Transactional
    public SubcategoriaResponse atualizar(UUID id, SubcategoriaRequest request) {
        Subcategoria subcategoria = subcategoriaRepository.buscarPorId(id)
                .orElseThrow(SubcategoriaNaoEncontradaException::new);
        Categoria categoria = buscarCategoria(request.categoriaId());
        if (!subcategoria.getCategoria().getId().equals(categoria.getId())
                && saidaRepository.existsBySubcategoria_Id(id)) {
            throw new RegistroVinculadoException("a subcategoria");
        }
        validarDuplicidade(categoria.getId(), request.nome(), id);
        boolean ativo = request.ativo() == null ? subcategoria.isAtivo() : request.ativo();
        boolean padrao = request.padrao() == null ? subcategoria.isPadrao() : request.padrao();
        if (!ativo || !categoria.isAtivo()) {
            padrao = false;
        }
        validarPadrao(categoria, ativo, padrao);
        if (padrao) {
            limparPadraoDoTipo(categoria.getTipo(), id);
        }

        subcategoria.setNome(request.nome().trim());
        subcategoria.setCategoria(categoria);
        subcategoria.setAtivo(ativo);
        subcategoria.setTipo(categoria.getTipo());
        subcategoria.setPadrao(padrao);
        return CategoriaMapper.toSubcategoriaResponse(subcategoria);
    }

    @Transactional
    public void excluir(UUID id) {
        Subcategoria subcategoria = subcategoriaRepository.buscarPorId(id)
                .orElseThrow(SubcategoriaNaoEncontradaException::new);
        if (saidaRepository.existsBySubcategoria_Id(id)) {
            throw new RegistroVinculadoException("a subcategoria");
        }
        subcategoriaRepository.delete(subcategoria);
    }

    private Categoria buscarCategoria(UUID id) {
        return categoriaRepository.buscarPorId(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);
    }

    private void validarDuplicidade(UUID categoriaId, String nome, UUID idAtual) {
        boolean duplicada = idAtual == null
                ? subcategoriaRepository.existsByCategoria_IdAndNomeIgnoreCase(categoriaId, nome.trim())
                : subcategoriaRepository.existsByCategoria_IdAndNomeIgnoreCaseAndIdNot(
                        categoriaId, nome.trim(), idAtual);
        if (duplicada) {
            throw new SubcategoriaDuplicadaException();
        }
    }

    private void validarPadrao(Categoria categoria, boolean ativo, boolean padrao) {
        if (padrao && (!ativo || !categoria.isAtivo())) {
            throw new PadraoCategoriaInativoException();
        }
    }

    private void limparPadraoDoTipo(br.com.rdrtech.categoria.TipoCategoria tipo, UUID idAtual) {
        List<Subcategoria> padroes = subcategoriaRepository.listarPadroesPorTipo(tipo).stream()
                .filter(subcategoria -> idAtual == null || !subcategoria.getId().equals(idAtual))
                .toList();
        padroes.forEach(subcategoria -> subcategoria.setPadrao(false));
        if (!padroes.isEmpty()) {
            subcategoriaRepository.flush();
        }
    }
}
