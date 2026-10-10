package br.com.rdrtech.pessoa;

import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pessoa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pessoa extends TenantAwareEntity {

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;
}
