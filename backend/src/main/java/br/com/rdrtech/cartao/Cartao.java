package br.com.rdrtech.cartao;

import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "cartao")
@Check(constraints = "(tipo = 'PRINCIPAL' and cartao_principal_id is null) or (tipo = 'ADICIONAL' and cartao_principal_id is not null)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cartao extends TenantAwareEntity {

    @Column(nullable = false, length = 50)
    private String nome;

    @Column(nullable = false, length = 100)
    private String banco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Bandeira bandeira;

    @Column(name = "ultimos_digitos", nullable = false, length = 4)
    private String ultimosDigitos;

    @Column(length = 7)
    private String cor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TipoCartao tipo = TipoCartao.PRINCIPAL;

    @Column(nullable = false)
    @Builder.Default
    private boolean virtual = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_principal_id")
    private Cartao cartaoPrincipal;

    /** Dia em que a fatura fecha; só no principal. Compras a partir deste dia vão para a fatura seguinte. */
    @Column(name = "dia_fechamento")
    private Integer diaFechamento;

    /** Dia de vencimento da fatura; só no principal. */
    @Column(name = "dia_vencimento")
    private Integer diaVencimento;
}
