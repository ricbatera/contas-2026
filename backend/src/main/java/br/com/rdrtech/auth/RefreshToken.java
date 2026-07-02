package br.com.rdrtech.auth;

import br.com.rdrtech.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Não estende BaseEntity/TenantAwareEntity: o id é atribuído manualmente (== jti do JWT de
 * refresh, definido em JwtService antes da persistência), o que conflita com a estratégia
 * @GeneratedValue(UUID) de BaseEntity. Também não é tenant-scoped — é infraestrutura de
 * segurança, dono é o próprio usuário via FK.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    @Column(name = "data_expiracao", nullable = false)
    private Instant dataExpiracao;

    @Column(nullable = false)
    private boolean revogado;

    @Column(name = "data_revogacao")
    private Instant dataRevogacao;
}
