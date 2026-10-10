package br.com.rdrtech.subcategoria;

import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "subcategoria",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subcategoria_categoria_nome",
                columnNames = {"categoria_id", "nome"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subcategoria extends TenantAwareEntity {

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCategoria tipo;

    @Column(nullable = false)
    @Builder.Default
    private boolean padrao = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;
}
