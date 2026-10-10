package br.com.rdrtech.pessoa;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.pessoa.dto.PessoaRequest;
import br.com.rdrtech.pessoa.dto.PessoaResponse;
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
@RequestMapping("/api/pessoas")
@RequiredArgsConstructor
@Tag(name = "Pessoas")
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    public SuccessResponse<List<PessoaResponse>> listar() {
        return SuccessResponse.of(pessoaService.listar());
    }

    @GetMapping("/{id}")
    public SuccessResponse<PessoaResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(pessoaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<PessoaResponse> criar(@Valid @RequestBody PessoaRequest request) {
        return SuccessResponse.of(pessoaService.criar(request), "Pessoa cadastrada com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<PessoaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody PessoaRequest request) {
        return SuccessResponse.of(pessoaService.atualizar(id, request), "Pessoa atualizada com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        pessoaService.excluir(id);
    }
}
