package br.com.rdrtech.dashboard;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.dashboard.dto.DashboardItemResponse;
import br.com.rdrtech.dashboard.dto.DashboardProjectionMonthResponse;
import br.com.rdrtech.dashboard.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Validated
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public SuccessResponse<DashboardResponse> consultar(
            @RequestParam @Min(1) @Max(12) int mes,
            @RequestParam @Min(1900) @Max(9999) int ano) {
        return SuccessResponse.of(dashboardService.consultar(mes, ano));
    }

    @GetMapping("/projecao")
    public SuccessResponse<List<DashboardProjectionMonthResponse>> consultarProjecao(
            @RequestParam @Min(1) @Max(12) int mes,
            @RequestParam @Min(1900) @Max(9999) int ano,
            @RequestParam @Min(3) @Max(24) int quantidade) {
        return SuccessResponse.of(dashboardService.consultarProjecao(mes, ano, quantidade));
    }

    @GetMapping("/saidas")
    public SuccessResponse<List<DashboardItemResponse>> listarSaidas(
            @RequestParam @Min(1) @Max(12) int mes,
            @RequestParam @Min(1900) @Max(9999) int ano) {
        return SuccessResponse.of(dashboardService.listarSaidas(mes, ano));
    }

    @GetMapping("/buckets")
    public SuccessResponse<List<DashboardItemResponse>> listarBuckets() {
        return SuccessResponse.of(dashboardService.listarBuckets());
    }

    @GetMapping("/contas-fixas")
    public SuccessResponse<List<DashboardItemResponse>> listarContasFixas(
            @RequestParam @Min(1) @Max(12) int mes,
            @RequestParam @Min(1900) @Max(9999) int ano) {
        return SuccessResponse.of(dashboardService.listarContasFixasMesAnterior(mes, ano));
    }
}
