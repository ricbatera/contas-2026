package br.com.rdrtech.fatura;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.fatura.dto.FaturaCompetenciaResponse;
import br.com.rdrtech.fatura.dto.FaturaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faturas")
@RequiredArgsConstructor
@Tag(name = "Faturas")
public class FaturaController {

    private final FaturaService faturaService;

    @GetMapping("/cartoes/{cartaoId}/competencias")
    public SuccessResponse<List<FaturaCompetenciaResponse>> listarCompetencias(@PathVariable UUID cartaoId) {
        return SuccessResponse.of(faturaService.listarCompetencias(cartaoId));
    }

    @GetMapping("/cartoes/{cartaoId}/{competencia}")
    public SuccessResponse<FaturaResponse> buscar(
            @PathVariable UUID cartaoId, @PathVariable int competencia) {
        return SuccessResponse.of(faturaService.buscar(cartaoId, competencia));
    }
}
