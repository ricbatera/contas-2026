package br.com.rdrtech.tenant;

import br.com.rdrtech.security.SecurityUtils;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Instanciada pelo Hibernate via reflexão (não é um bean Spring) para resolver o valor do
 * parâmetro "tenantId" do filtro automático de {@link TenantAwareEntity} a cada query. Usa
 * {@link SecurityUtils} estaticamente porque não há injeção de dependência disponível aqui.
 */
public class CurrentTenantParamResolver implements Supplier<UUID> {

    @Override
    public UUID get() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum usuário autenticado no contexto atual — impossível resolver tenant_id"));
    }
}
