package br.com.rdrtech.categoria;

import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "categoria",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_categoria_tenant_tipo_nome",
                columnNames = {"tenant_id", "tipo", "nome"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria extends TenantAwareEntity {

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCategoria tipo;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Subcategoria> subcategorias = new ArrayList<>();
}
