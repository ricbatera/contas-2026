package br.com.rdrtech.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Entidade só de teste (src/test/java) usada exclusivamente para provar o mecanismo de
 * isolamento por tenant antes de qualquer entidade de negócio real depender dele.
 */
@Entity
@Table(name = "tenant_test_entity")
@Getter
@Setter
public class TenantTestEntity extends TenantAwareEntity {

    @Column(nullable = false)
    private String descricao;
}
