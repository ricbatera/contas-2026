package br.com.rdrtech.saida;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.saida.dto.ContaFixaPreviaResponse;
import br.com.rdrtech.saida.dto.GerarContasFixasRequest;
import br.com.rdrtech.saida.dto.GerarContasFixasResponse;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.saida.dto.SaidaResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saidas")
@RequiredArgsConstructor
@Tag(name = "Saídas")
public class SaidaController {

    private final SaidaService saidaService;
    private final ContasFixasService contasFixasService;

    @GetMapping
    public SuccessResponse<List<SaidaResponse>> listar() {
        return SuccessResponse.of(saidaService.listar());
    }

    @GetMapping("/{id}")
    public SuccessResponse<SaidaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(saidaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<SaidaResponse> criar(@Valid @RequestBody SaidaRequest request) {
        return SuccessResponse.of(saidaService.criar(request), "Saída cadastrada com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<SaidaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody SaidaRequest request) {
        return SuccessResponse.of(saidaService.atualizar(id, request), "Saída atualizada com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        saidaService.excluir(id);
    }

    /** Exclui as parcelas a partir da informada (serviço cancelado no cartão); as anteriores continuam. */
    @DeleteMapping("/{id}/parcelas")
    public SuccessResponse<SaidaResponse> excluirParcelasAPartirDe(
            @PathVariable UUID id, @RequestParam int aPartirDe) {
        return SuccessResponse.of(
                saidaService.excluirParcelasAPartirDe(id, aPartirDe), "Parcelas futuras excluídas com sucesso");
    }

    @GetMapping("/contas-fixas/previa")
    public SuccessResponse<List<ContaFixaPreviaResponse>> previaContasFixas(
            @RequestParam int mes, @RequestParam int ano) {
        return SuccessResponse.of(contasFixasService.previa(mes, ano));
    }

    @PostMapping("/contas-fixas")
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<GerarContasFixasResponse> gerarContasFixas(
            @Valid @RequestBody GerarContasFixasRequest request) {
        return SuccessResponse.of(contasFixasService.gerar(request), "Contas fixas geradas com sucesso");
    }
}
