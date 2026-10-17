package br.com.rdrtech.bucket;

import br.com.rdrtech.bucket.dto.BucketResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BucketMapper {

    private BucketMapper() {
    }

    public static BucketResponse toResponse(Bucket bucket, BigDecimal consumido, int mes, int ano) {
        BigDecimal valorMensal = bucket.getValorMensal();
        BigDecimal saldo = valorMensal.subtract(consumido);
        BigDecimal percentual = consumido
                .multiply(BigDecimal.valueOf(100))
                .divide(valorMensal, 2, RoundingMode.HALF_UP);
        return new BucketResponse(
                bucket.getId(),
                bucket.getSubcategoria().getCategoria().getId(),
                bucket.getSubcategoria().getCategoria().getNome(),
                bucket.getSubcategoria().getId(),
                bucket.getSubcategoria().getNome(),
                bucket.getPessoa() != null ? bucket.getPessoa().getId() : null,
                bucket.getPessoa() != null
                        ? bucket.getPessoa().getNome()
                        : bucket.getUsuario().getNome() + " " + bucket.getUsuario().getSobrenome(),
                bucket.getPessoa() == null,
                valorMensal,
                consumido,
                saldo,
                percentual,
                mes,
                ano);
    }
}
