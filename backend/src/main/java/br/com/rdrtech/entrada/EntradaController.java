package br.com.rdrtech.entrada;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.entrada.dto.EntradaOcorrenciaResponse;
import br.com.rdrtech.entrada.dto.EntradaRequest;
import br.com.rdrtech.entrada.dto.EntradaResponse;
import br.com.rdrtech.entrada.dto.ReceberOcorrenciaRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entradas")
@RequiredArgsConstructor
@Validated
@Tag(name = "Entradas")
public class EntradaController {

    private final EntradaService entradaService;

    @GetMapping
    public SuccessResponse<List<EntradaResponse>> listar() {
        return SuccessResponse.of(entradaService.listar());
    }

    @GetMapping("/{id}")
    public SuccessResponse<EntradaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(entradaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<EntradaResponse> criar(@Valid @RequestBody EntradaRequest request) {
        return SuccessResponse.of(entradaService.criar(request), "Entrada cadastrada com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<EntradaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody EntradaRequest request) {
        return SuccessResponse.of(entradaService.atualizar(id, request), "Entrada atualizada com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        entradaService.excluir(id);
    }

    @GetMapping("/ocorrencias")
    public SuccessResponse<List<EntradaOcorrenciaResponse>> listarOcorrencias(
            @RequestParam @Min(1) @Max(12) int mes,
            @RequestParam @Min(1900) @Max(9999) int ano) {
        return SuccessResponse.of(entradaService.listarOcorrencias(mes, ano));
    }

    @PostMapping("/ocorrencias/{id}/receber")
    public SuccessResponse<EntradaOcorrenciaResponse> receber(
            @PathVariable UUID id, @Valid @RequestBody ReceberOcorrenciaRequest request) {
        return SuccessResponse.of(entradaService.receber(id, request), "Recebimento registrado");
    }

    @PostMapping("/ocorrencias/{id}/cancelar")
    public SuccessResponse<EntradaOcorrenciaResponse> cancelar(@PathVariable UUID id) {
        return SuccessResponse.of(entradaService.cancelar(id), "Ocorrência cancelada");
    }

    @PostMapping("/ocorrencias/{id}/reabrir")
    public SuccessResponse<EntradaOcorrenciaResponse> reabrir(@PathVariable UUID id) {
        return SuccessResponse.of(entradaService.reabrir(id), "Ocorrência reaberta");
    }
}
