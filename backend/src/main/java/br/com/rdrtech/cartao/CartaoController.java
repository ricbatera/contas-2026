package br.com.rdrtech.cartao;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.cartao.dto.CartaoRequest;
import br.com.rdrtech.cartao.dto.CartaoResponse;
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
@RequestMapping("/api/cartoes")
@RequiredArgsConstructor
@Tag(name = "Cartões")
public class CartaoController {

    private final CartaoService cartaoService;

    @GetMapping
    public SuccessResponse<List<CartaoResponse>> listar() {
        return SuccessResponse.of(cartaoService.listar());
    }

    @GetMapping("/principais")
    public SuccessResponse<List<CartaoResponse>> listarPrincipais() {
        return SuccessResponse.of(cartaoService.listarPrincipais());
    }

    @GetMapping("/{id}")
    public SuccessResponse<CartaoResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(cartaoService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<CartaoResponse> criar(@Valid @RequestBody CartaoRequest request) {
        return SuccessResponse.of(cartaoService.criar(request), "Cartão cadastrado com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<CartaoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody CartaoRequest request) {
        return SuccessResponse.of(cartaoService.atualizar(id, request), "Cartão atualizado com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        cartaoService.excluir(id);
    }
}
