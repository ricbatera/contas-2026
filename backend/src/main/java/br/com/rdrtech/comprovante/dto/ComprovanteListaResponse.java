package br.com.rdrtech.comprovante.dto;

import br.com.rdrtech.comprovante.ModalidadeComprovante;
import br.com.rdrtech.comprovante.StatusComprovante;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ComprovanteListaResponse(
        UUID id,
        StatusComprovante status,
        ModalidadeComprovante modalidade,
        String descricao,
        String estabelecimento,
        LocalDate dataCompra,
        BigDecimal valorTotal,
        int quantidadeParcelas,
        UUID cartaoId,
        String cartaoNome,
        String cartaoUltimosDigitos,
        String erroLeitura,
        LocalDateTime criadoEm) {
}
