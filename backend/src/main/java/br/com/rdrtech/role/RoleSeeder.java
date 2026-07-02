package br.com.rdrtech.role;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Garante a existência das roles USER/ADMIN no boot — substitui Flyway/data.sql enquanto não
 * há ferramenta de migração no projeto. Idempotente: verifica existência antes de inserir.
 */
@Component
@RequiredArgsConstructor
public class RoleSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (NomePerfil nomePerfil : NomePerfil.values()) {
            roleRepository.findByNome(nomePerfil)
                    .orElseGet(() -> roleRepository.save(Role.builder().nome(nomePerfil).build()));
        }
    }
}
