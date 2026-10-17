package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoListaResponse;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoRequest;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoResponse;
import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoStatusResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faturas/importacoes")
@RequiredArgsConstructor
@Tag(name = "Importação de faturas")
public class FaturaImportacaoController {

    private final FaturaImportacaoService importacaoService;

    @GetMapping
    public SuccessResponse<List<FaturaImportacaoListaResponse>> listar() {
        return SuccessResponse.of(importacaoService.listar());
    }

    @GetMapping("/{id}")
    public SuccessResponse<FaturaImportacaoResponse> buscarPorId(@PathVariable UUID id) {
        return SuccessResponse.of(importacaoService.buscarPorId(id));
    }

    @GetMapping("/{id}/status")
    public SuccessResponse<FaturaImportacaoStatusResponse> buscarStatus(@PathVariable UUID id) {
        return SuccessResponse.of(importacaoService.buscarStatus(id));
    }

    /**
     * Recebe o PDF em base64 e valida arquivo e senha na hora. A leitura por IA e a conciliação
     * rodam em segundo plano: responde 202 com status PROCESSANDO, e o cliente acompanha por
     * {@code GET /{id}/status} até CONCLUIDA ou ERRO.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuccessResponse<FaturaImportacaoResponse> importar(@Valid @RequestBody FaturaImportacaoRequest request) {
        return SuccessResponse.of(importacaoService.importar(request), "Fatura recebida; leitura em andamento");
    }

    @PostMapping("/{id}/reprocessar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuccessResponse<FaturaImportacaoResponse> reprocessar(@PathVariable UUID id) {
        return SuccessResponse.of(importacaoService.reprocessar(id), "Reprocessamento em andamento");
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> baixarPdf(@PathVariable UUID id) {
        var arquivo = importacaoService.baixarPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(arquivo.nomeArquivo(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(arquivo.conteudo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        importacaoService.excluir(id);
    }
}
