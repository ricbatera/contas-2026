package br.com.rdrtech.tenant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantTestEntityRepository extends JpaRepository<TenantTestEntity, UUID> {
}
