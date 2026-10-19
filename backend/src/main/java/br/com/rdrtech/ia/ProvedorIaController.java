package br.com.rdrtech.ia;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.ia.dto.ModelosIaRequest;
import br.com.rdrtech.ia.dto.ModelosIaResponse;
import br.com.rdrtech.ia.dto.ProvedorIaRequest;
import br.com.rdrtech.ia.dto.ProvedorIaResponse;
import br.com.rdrtech.ia.dto.TesteIaRequest;
import br.com.rdrtech.ia.dto.TesteIaResponse;
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
@RequestMapping("/api/ia/provedores")
@RequiredArgsConstructor
@Tag(name = "Provedores de IA")
public class ProvedorIaController {

    private final ProvedorIaService provedorIaService;

    @GetMapping
    public SuccessResponse<List<ProvedorIaResponse>> listar() {
        return SuccessResponse.of(provedorIaService.listar());
    }

    @PostMapping("/modelos")
    public SuccessResponse<ModelosIaResponse> listarModelos(@Valid @RequestBody ModelosIaRequest request) {
        return SuccessResponse.of(provedorIaService.listarModelos(request));
    }

    @GetMapping("/{id}")
    public SuccessResponse<ProvedorIaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(provedorIaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<ProvedorIaResponse> criar(@Valid @RequestBody ProvedorIaRequest request) {
        return SuccessResponse.of(provedorIaService.criar(request), "Provedor de IA cadastrado com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<ProvedorIaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody ProvedorIaRequest request) {
        return SuccessResponse.of(provedorIaService.atualizar(id, request), "Provedor de IA atualizado com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        provedorIaService.excluir(id);
    }

    @PostMapping("/{id}/testar")
    public SuccessResponse<TesteIaResponse> testar(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TesteIaRequest request) {
        return SuccessResponse.of(provedorIaService.testar(id, request == null ? null : request.prompt()));
    }
}
