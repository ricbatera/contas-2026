package br.com.rdrtech.comprovante;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.comprovante.dto.ComprovanteEdicaoRequest;
import br.com.rdrtech.comprovante.dto.ComprovanteListaResponse;
import br.com.rdrtech.comprovante.dto.ComprovanteRequest;
import br.com.rdrtech.comprovante.dto.ComprovanteResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/comprovantes")
@RequiredArgsConstructor
@Tag(name = "Comprovantes de cartão")
public class ComprovanteController {

    private final ComprovanteService comprovanteService;

    @GetMapping
    public SuccessResponse<List<ComprovanteListaResponse>> listar(
            @RequestParam(required = false) StatusComprovante status) {
        return SuccessResponse.of(comprovanteService.listar(status));
    }

    @GetMapping("/{id}")
    public SuccessResponse<ComprovanteResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(comprovanteService.buscarPorId(id));
    }

    @GetMapping("/{id}/imagem")
    public ResponseEntity<byte[]> baixarImagem(@PathVariable UUID id) {
        var imagem = comprovanteService.baixarImagem(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagem.tipoMidia()))
                .body(imagem.conteudo());
    }

    /** Recebe a foto em base64 e devolve o comprovante já lido pela IA, pronto para revisão. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<ComprovanteResponse> enviar(@Valid @RequestBody ComprovanteRequest request) {
        return SuccessResponse.of(comprovanteService.enviar(request), "Comprovante lido; revise e aprove");
    }

    @PostMapping("/{id}/reler")
    public SuccessResponse<ComprovanteResponse> reler(@PathVariable UUID id) {
        return SuccessResponse.of(comprovanteService.reler(id), "Comprovante lido novamente");
    }

    @PutMapping("/{id}")
    public SuccessResponse<ComprovanteResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody ComprovanteEdicaoRequest request) {
        return SuccessResponse.of(comprovanteService.atualizar(id, request), "Comprovante atualizado");
    }

    @PostMapping("/{id}/aprovar")
    public SuccessResponse<ComprovanteResponse> aprovar(@PathVariable UUID id) {
        return SuccessResponse.of(comprovanteService.aprovar(id), "Comprovante aprovado e incluído nas saídas");
    }

    @PostMapping("/{id}/rejeitar")
    public SuccessResponse<ComprovanteResponse> rejeitar(@PathVariable UUID id) {
        return SuccessResponse.of(comprovanteService.rejeitar(id), "Comprovante rejeitado");
    }

    @PostMapping("/{id}/reabrir")
    public SuccessResponse<ComprovanteResponse> reabrir(@PathVariable UUID id) {
        return SuccessResponse.of(comprovanteService.reabrir(id), "Comprovante reaberto");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        comprovanteService.excluir(id);
    }
}
