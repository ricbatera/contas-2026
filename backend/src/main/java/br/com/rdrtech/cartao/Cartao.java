package br.com.rdrtech.cartao;

import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cartao")
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
}
