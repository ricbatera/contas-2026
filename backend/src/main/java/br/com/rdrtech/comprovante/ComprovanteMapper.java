package br.com.rdrtech.comprovante;

import br.com.rdrtech.comprovante.dto.ComprovanteResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

public final class ComprovanteMapper {

    private ComprovanteMapper() {
    }

    public static ComprovanteResponse toResponse(ComprovanteCartao comprovante) {
        var cartao = comprovante.getCartao();
        var categoria = comprovante.getCategoria();
        var subcategoria = comprovante.getSubcategoria();
        return new ComprovanteResponse(
                comprovante.getId(),
                comprovante.getNomeArquivo(),
                comprovante.getTipoMidia(),
                comprovante.getTamanhoBytes(),
                comprovante.getStatus(),
                comprovante.getErroLeitura(),
                avisos(comprovante.getAvisos()),
                comprovante.getEstabelecimento(),
                comprovante.getUltimosDigitosLidos(),
                comprovante.getBandeiraLida(),
                comprovante.getProvedorModelo(),
                comprovante.getModalidade(),
                comprovante.getDescricao(),
                comprovante.getDataCompra(),
                comprovante.getValorTotal(),
                comprovante.getQuantidadeParcelas(),
                valorPorParcela(comprovante.getValorTotal(), comprovante.getQuantidadeParcelas()),
                comprovante.getPrimeiroVencimento(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                cartao != null ? cartao.getUltimosDigitos() : null,
                categoria != null ? categoria.getId() : null,
                categoria != null ? categoria.getNome() : null,
                subcategoria != null ? subcategoria.getId() : null,
                subcategoria != null ? subcategoria.getNome() : null,
                comprovante.getSaida() != null ? comprovante.getSaida().getId() : null,
                comprovante.getAuditoria().getCreatedAt());
    }

    /** O comprovante traz o total; a saída é gravada por parcela, arredondada a centavos. */
    static BigDecimal valorPorParcela(BigDecimal valorTotal, int quantidadeParcelas) {
        if (valorTotal == null) {
            return null;
        }
        return valorTotal.divide(BigDecimal.valueOf(Math.max(1, quantidadeParcelas)), 2, RoundingMode.HALF_UP);
    }

    private static List<String> avisos(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return Arrays.stream(texto.split("\n")).filter(linha -> !linha.isBlank()).toList();
    }
}
