package br.com.rdrtech.tenant;

import br.com.rdrtech.audit.BaseEntity;
import br.com.rdrtech.security.SecurityUtils;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Entidades de negócio (Cartão, Lançamento, etc.) devem estender esta classe em vez de
 * {@link BaseEntity} para ganhar isolamento automático por usuário dono dos dados.
 *
 * <p>Usa {@code @FilterDef(autoEnabled = true)} (Hibernate 6.4+) com um {@code resolver} —
 * NÃO {@code @org.hibernate.annotations.TenantId}/{@code CurrentTenantIdentifierResolver}, que
 * foi testado e descartado: registrar um {@code CurrentTenantIdentifierResolver} torna a
 * SessionFactory inteira "multi-tenant", exigindo um tenant resolvível para abrir QUALQUER
 * Session — inclusive para repositórios sem nenhuma relação com tenant (Usuario, Role), o que
 * quebrava o boot da aplicação. O filtro automático abaixo é escopado apenas às entidades que
 * o declaram (via herança de {@link TenantAwareEntity}), sem esse efeito colateral global.
 */
@MappedSuperclass
@FilterDef(
        name = "tenantFilter",
        autoEnabled = true,
        parameters = @ParamDef(name = "tenantId", type = UUID.class, resolver = CurrentTenantParamResolver.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    private void preencherTenantId() {
        if (tenantId == null) {
            tenantId = SecurityUtils.getCurrentUserId()
                    .orElseThrow(() -> new IllegalStateException(
                            "Nenhum usuário autenticado no contexto atual — impossível resolver tenant_id"));
        }
    }
}
