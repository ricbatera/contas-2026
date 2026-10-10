package br.com.rdrtech.saida;

import br.com.rdrtech.fatura.Fatura;
import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "saida_parcela",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saida_parcela_numero",
                columnNames = {"saida_id", "numero"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaidaParcela extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saida_id", nullable = false)
    private Saida saida;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id")
    private Fatura fatura;
}
