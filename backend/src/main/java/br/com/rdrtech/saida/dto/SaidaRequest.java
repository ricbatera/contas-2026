package br.com.rdrtech.saida.dto;

import br.com.rdrtech.saida.FormaPagamento;
import br.com.rdrtech.saida.ModoResponsabilidade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaidaRequest(
        @NotBlank @Size(max = 120) String descricao,
        @Size(max = 120) String descricaoFatura,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valorPorParcela,
        @NotNull @Min(1) @Max(36) Integer quantidadeParcelas,
        @NotNull FormaPagamento formaPagamento,
        LocalDate dataInicial,
        LocalDate primeiroVencimento,
        UUID cartaoId,
        @NotNull UUID categoriaId,
        @NotNull UUID subcategoriaId,
        @NotNull ModoResponsabilidade modoResponsabilidade,
        UUID responsavelPessoaId,
        @Valid List<@NotNull SaidaParteRequest> partes,
        boolean contaFixa) {

    public SaidaRequest(
            String descricao,
            String descricaoFatura,
            BigDecimal valorPorParcela,
            Integer quantidadeParcelas,
            FormaPagamento formaPagamento,
            LocalDate dataInicial,
            LocalDate primeiroVencimento,
            UUID cartaoId,
            UUID categoriaId,
            UUID subcategoriaId,
            ModoResponsabilidade modoResponsabilidade,
            UUID responsavelPessoaId,
            List<SaidaParteRequest> partes) {
        this(
                descricao,
                descricaoFatura,
                valorPorParcela,
                quantidadeParcelas,
                formaPagamento,
                dataInicial,
                primeiroVencimento,
                cartaoId,
                categoriaId,
                subcategoriaId,
                modoResponsabilidade,
                responsavelPessoaId,
                partes,
                false);
    }

    public SaidaRequest {
        partes = partes == null ? List.of() : List.copyOf(partes);
    }
}
