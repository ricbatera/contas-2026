package br.com.rdrtech;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Recria o schema do banco de teste do zero (clean + migrate) a cada contexto Spring, para que
 * toda execução valide as migrations completas em PostgreSQL e não dependa de dados anteriores.
 */
@Configuration
@Profile("test")
public class TestFlywayConfig {

    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
