package br.com.rdrtech.presaida;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.presaida.dto.AprovacaoLoteRequest;
import br.com.rdrtech.presaida.dto.AprovacaoLoteResponse;
import br.com.rdrtech.presaida.dto.PreSaidaRequest;
import br.com.rdrtech.presaida.dto.PreSaidaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pre-saidas")
@RequiredArgsConstructor
@Tag(name = "Pré-saídas")
public class PreSaidaController {

    private final PreSaidaService preSaidaService;

    @GetMapping
    public SuccessResponse<List<PreSaidaResponse>> listar(
            @RequestParam(required = false) StatusPreSaida status,
            @RequestParam(required = false) UUID importacaoId) {
        return SuccessResponse.of(preSaidaService.listar(status, importacaoId));
    }

    @GetMapping("/{id}")
    public SuccessResponse<PreSaidaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(preSaidaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public SuccessResponse<PreSaidaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody PreSaidaRequest request) {
        return SuccessResponse.of(preSaidaService.atualizar(id, request), "Pré-saída atualizada");
    }

    @PostMapping("/{id}/aprovar")
    public SuccessResponse<PreSaidaResponse> aprovar(@PathVariable UUID id) {
        return SuccessResponse.of(preSaidaService.aprovar(id), "Pré-saída aprovada e incluída nas saídas");
    }

    @PostMapping("/aprovar-lote")
    public SuccessResponse<AprovacaoLoteResponse> aprovarLote(@Valid @RequestBody AprovacaoLoteRequest request) {
        return SuccessResponse.of(preSaidaService.aprovarLote(request.ids()));
    }

    @PostMapping("/{id}/vincular")
    public SuccessResponse<PreSaidaResponse> vincular(@PathVariable UUID id) {
        return SuccessResponse.of(preSaidaService.vincular(id), "Pré-saída vinculada à saída existente");
    }

    @PostMapping("/{id}/descartar-sugestao")
    public SuccessResponse<PreSaidaResponse> descartarSugestao(@PathVariable UUID id) {
        return SuccessResponse.of(preSaidaService.descartarSugestao(id), "Sugestão descartada");
    }

    @PostMapping("/{id}/rejeitar")
    public SuccessResponse<PreSaidaResponse> rejeitar(@PathVariable UUID id) {
        return SuccessResponse.of(preSaidaService.rejeitar(id), "Pré-saída rejeitada");
    }

    @PostMapping("/{id}/reabrir")
    public SuccessResponse<PreSaidaResponse> reabrir(@PathVariable UUID id) {
        return SuccessResponse.of(preSaidaService.reabrir(id), "Pré-saída reaberta");
    }
}
