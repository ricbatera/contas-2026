package br.com.rdrtech.categoria;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.categoria.dto.CategoriaRequest;
import br.com.rdrtech.categoria.dto.CategoriaResponse;
import br.com.rdrtech.subcategoria.SubcategoriaService;
import br.com.rdrtech.subcategoria.dto.SubcategoriaCreateRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
@Tag(name = "Categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;
    private final SubcategoriaService subcategoriaService;

    @GetMapping
    public SuccessResponse<List<CategoriaResponse>> listar() {
        return SuccessResponse.of(categoriaService.listar());
    }

    @GetMapping("/{id}")
    public SuccessResponse<CategoriaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(categoriaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest request) {
        return SuccessResponse.of(categoriaService.criar(request), "Categoria cadastrada com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<CategoriaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody CategoriaRequest request) {
        return SuccessResponse.of(categoriaService.atualizar(id, request), "Categoria atualizada com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        categoriaService.excluir(id);
    }

    @PostMapping("/{categoriaId}/subcategorias")
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<SubcategoriaResponse> criarSubcategoria(
            @PathVariable UUID categoriaId, @Valid @RequestBody SubcategoriaCreateRequest request) {
        return SuccessResponse.of(subcategoriaService.criar(categoriaId, request), "Subcategoria cadastrada com sucesso");
    }

    @GetMapping("/{categoriaId}/subcategorias")
    public SuccessResponse<List<SubcategoriaResponse>> listarSubcategorias(@PathVariable UUID categoriaId) {
        return SuccessResponse.of(subcategoriaService.listar(categoriaId));
    }
}
