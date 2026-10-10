package br.com.rdrtech.subcategoria;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.subcategoria.dto.SubcategoriaRequest;
import br.com.rdrtech.subcategoria.dto.SubcategoriaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subcategorias")
@RequiredArgsConstructor
@Tag(name = "Subcategorias")
public class SubcategoriaController {

    private final SubcategoriaService subcategoriaService;

    @GetMapping("/{id}")
    public SuccessResponse<SubcategoriaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(subcategoriaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public SuccessResponse<SubcategoriaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody SubcategoriaRequest request) {
        return SuccessResponse.of(subcategoriaService.atualizar(id, request), "Subcategoria atualizada com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        subcategoriaService.excluir(id);
    }
}
