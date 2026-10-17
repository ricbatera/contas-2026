package br.com.rdrtech.bucket;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.bucket.dto.BucketRequest;
import br.com.rdrtech.bucket.dto.BucketResponse;
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
@RequestMapping("/api/buckets")
@RequiredArgsConstructor
@Validated
@Tag(name = "Buckets")
public class BucketController {

    private final BucketService bucketService;

    @GetMapping
    public SuccessResponse<List<BucketResponse>> listar(
            @RequestParam @Min(1) @Max(12) int mes,
            @RequestParam @Min(1900) @Max(9999) int ano) {
        return SuccessResponse.of(bucketService.listar(mes, ano));
    }

    @GetMapping("/{id}")
    public SuccessResponse<BucketResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(bucketService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<BucketResponse> criar(@Valid @RequestBody BucketRequest request) {
        return SuccessResponse.of(bucketService.criar(request), "Bucket cadastrado com sucesso");
    }

    @PutMapping("/{id}")
    public SuccessResponse<BucketResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody BucketRequest request) {
        return SuccessResponse.of(bucketService.atualizar(id, request), "Bucket atualizado com sucesso");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        bucketService.excluir(id);
    }
}
