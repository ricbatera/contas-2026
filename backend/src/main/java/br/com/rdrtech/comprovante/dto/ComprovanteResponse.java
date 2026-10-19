package br.com.rdrtech.comprovante.dto;

import br.com.rdrtech.comprovante.ModalidadeComprovante;
import br.com.rdrtech.comprovante.StatusComprovante;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Comprovante sem a imagem, que é baixada por {@code GET /api/comprovantes/{id}/imagem}. */
public record ComprovanteResponse(
        UUID id,
        String nomeArquivo,
        String tipoMidia,
        int tamanhoBytes,
        StatusComprovante status,
        String erroLeitura,
        List<String> avisos,
        String estabelecimento,
        String ultimosDigitosLidos,
        String bandeiraLida,
        String provedorModelo,
        ModalidadeComprovante modalidade,
        String descricao,
        LocalDate dataCompra,
        BigDecimal valorTotal,
        int quantidadeParcelas,
        BigDecimal valorPorParcela,
        LocalDate primeiroVencimento,
        UUID cartaoId,
        String cartaoNome,
        String cartaoUltimosDigitos,
        UUID categoriaId,
        String categoriaNome,
        UUID subcategoriaId,
        String subcategoriaNome,
        UUID saidaId,
        LocalDateTime criadoEm) {
}
