package br.com.rdrtech.saida;

import br.com.rdrtech.pessoa.Pessoa;
import br.com.rdrtech.tenant.TenantAwareEntity;
import br.com.rdrtech.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "saida_responsavel",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saida_responsavel_usuario",
                columnNames = {"saida_id", "usuario_id"}))
@Check(constraints = "(usuario_id is not null and pessoa_id is null) or "
        + "(usuario_id is null and pessoa_id is not null)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaidaResponsavel extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saida_id", nullable = false)
    private Saida saida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    @Column(name = "valor_por_parcela", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPorParcela;
}
