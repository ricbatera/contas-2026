package br.com.rdrtech.fatura;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.saida.SaidaParcela;
import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "fatura",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fatura_cartao_competencia",
                columnNames = {"tenant_id", "cartao_principal_id", "competencia"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fatura extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cartao_principal_id", nullable = false)
    private Cartao cartaoPrincipal;

    @Column(nullable = false)
    private int competencia;

    @Column(nullable = false)
    private LocalDate vencimento;

    @OneToMany(mappedBy = "fatura")
    @Builder.Default
    private List<SaidaParcela> parcelas = new ArrayList<>();

    public void adicionarParcela(SaidaParcela parcela) {
        parcelas.add(parcela);
        parcela.setFatura(this);
    }

    public void removerParcela(SaidaParcela parcela) {
        parcelas.remove(parcela);
        parcela.setFatura(null);
    }

    public BigDecimal getTotal() {
        return parcelas.stream()
                .map(SaidaParcela::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
