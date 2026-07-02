package br.com.rdrtech.audit;

import br.com.rdrtech.security.SecurityUtils;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return SecurityUtils.getCurrentUserId()
                .map(UUID::toString)
                .or(() -> Optional.of("system"));
    }
}
