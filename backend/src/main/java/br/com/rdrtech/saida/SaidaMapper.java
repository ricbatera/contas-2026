package br.com.rdrtech.saida;

import br.com.rdrtech.fatura.Fatura;
import br.com.rdrtech.saida.dto.SaidaParcelaResponse;
import br.com.rdrtech.saida.dto.SaidaResponse;
import br.com.rdrtech.saida.dto.SaidaResponsavelResponse;
import java.math.BigDecimal;
import java.util.Comparator;

public final class SaidaMapper {

    private SaidaMapper() {
    }

    public static SaidaResponse toResponse(Saida saida) {
        var responsabilidades = saida.getResponsabilidades().stream()
                .map(responsavel -> new SaidaResponsavelResponse(
                        responsavel.getPessoa() != null ? responsavel.getPessoa().getId() : null,
                        responsavel.getUsuario() != null ? responsavel.getUsuario().getId() : null,
                        responsavel.getPessoa() != null
                                ? responsavel.getPessoa().getNome()
                                : responsavel.getUsuario().getNome() + " " + responsavel.getUsuario().getSobrenome(),
                        responsavel.getValorPorParcela()))
                .toList();
        var parcelas = saida.getParcelas().stream()
                .sorted(Comparator.comparingInt(SaidaParcela::getNumero))
                .map(parcela -> new SaidaParcelaResponse(
                        parcela.getId(),
                        parcela.getNumero(),
                        parcela.getValor(),
                        parcela.getVencimento(),
                        parcela.getFatura() != null ? parcela.getFatura().getId() : null,
                        parcela.getFatura() != null ? parcela.getFatura().getVencimento() : null,
                        parcela.getFatura() != null ? parcela.getFatura().getTotal() : null))
                .toList();
        var total = saida.getValorPorParcela().multiply(BigDecimal.valueOf(saida.getQuantidadeParcelas()));
        var pessoaDireta = saida.getModoResponsabilidade() == ModoResponsabilidade.PESSOA
                ? responsabilidades.stream().filter(item -> item.pessoaId() != null).findFirst().orElse(null)
                : null;
        Fatura faturaInicial = saida.getParcelas().stream()
                .min(Comparator.comparingInt(SaidaParcela::getNumero))
                .map(SaidaParcela::getFatura)
                .orElse(null);

        return new SaidaResponse(
                saida.getId(),
                saida.getDescricao(),
                saida.getDescricaoFatura(),
                saida.getValorPorParcela(),
                total,
                saida.getQuantidadeParcelas(),
                saida.getFormaPagamento(),
                saida.getModoResponsabilidade(),
                saida.getDataInicial(),
                saida.getPrimeiroVencimento(),
                saida.getCartao() != null ? saida.getCartao().getId() : null,
                saida.getCartao() != null ? saida.getCartao().getNome() : null,
                saida.getCategoria().getId(),
                saida.getCategoria().getNome(),
                saida.getSubcategoria().getId(),
                saida.getSubcategoria().getNome(),
                pessoaDireta != null ? pessoaDireta.pessoaId() : null,
                responsabilidades,
                parcelas,
                saida.getAuditoria() != null ? saida.getAuditoria().getCreatedAt() : null,
                saida.isContaFixa());
    }
}
