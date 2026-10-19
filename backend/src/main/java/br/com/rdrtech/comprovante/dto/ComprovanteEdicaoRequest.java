package br.com.rdrtech.comprovante.dto;

import br.com.rdrtech.comprovante.ModalidadeComprovante;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ajustes do usuário antes de aprovar. Os campos podem ficar vazios ao salvar um rascunho; a
 * aprovação exige o que a saída precisa. Sem {@code primeiroVencimento}, ele é recalculado pelo
 * cartão e pela data da compra.
 */
public record ComprovanteEdicaoRequest(
        @NotNull ModalidadeComprovante modalidade,
        @Size(max = 120) String descricao,
        UUID cartaoId,
        UUID categoriaId,
        UUID subcategoriaId,
        LocalDate dataCompra,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valorTotal,
        @NotNull @Min(1) @Max(60) Integer quantidadeParcelas,
        LocalDate primeiroVencimento) {
}
