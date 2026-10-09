package br.com.rdrtech.contabancaria;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.contabancaria.dto.ContaBancariaRequest;
import br.com.rdrtech.contabancaria.dto.ContaBancariaResponse;
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
@RequestMapping("/api/contas-bancarias")
@RequiredArgsConstructor
@Tag(name = "Contas Bancárias")
public class ContaBancariaController {

    private final ContaBancariaService contaBancariaService;

    @GetMapping
    public SuccessResponse<List<ContaBancariaResponse>> listar() {
        return SuccessResponse.of(contaBancariaService.listar());
    }

    @GetMapping("/{id}")
    public SuccessResponse<ContaBancariaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(contaBancariaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<ContaBancariaResponse> criar(@Valid @RequestBody ContaBancariaRequest request) {
        return SuccessResponse.of(contaBancariaService.criar(request), "Conta bancária cadastrada com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<ContaBancariaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody ContaBancariaRequest request) {
        return SuccessResponse.of(contaBancariaService.atualizar(id, request), "Conta bancária atualizada com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        contaBancariaService.excluir(id);
    }
}
