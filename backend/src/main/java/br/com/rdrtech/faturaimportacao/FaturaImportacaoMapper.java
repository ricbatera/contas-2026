package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.faturaimportacao.dto.FaturaImportacaoResponse;
import br.com.rdrtech.faturaimportacao.dto.ResumoImportacao;
import br.com.rdrtech.presaida.dto.PreSaidaResponse;
import java.util.List;

public final class FaturaImportacaoMapper {

    private FaturaImportacaoMapper() {
    }

    public static FaturaImportacaoResponse toResponse(
            FaturaImportacao importacao, ResumoImportacao resumo, List<PreSaidaResponse> preSaidas) {
        var cartao = importacao.getCartaoPrincipal();
        return new FaturaImportacaoResponse(
                importacao.getId(),
                importacao.getNomeArquivo(),
                importacao.getTamanhoBytes(),
                importacao.getStatus(),
                importacao.getErro(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                cartao != null ? cartao.getUltimosDigitos() : null,
                importacao.getCompetencia(),
                importacao.getVencimento(),
                importacao.getTotalPdf(),
                importacao.getProvedorModelo(),
                importacao.getAuditoria().getCreatedAt(),
                resumo,
                preSaidas);
    }
}
