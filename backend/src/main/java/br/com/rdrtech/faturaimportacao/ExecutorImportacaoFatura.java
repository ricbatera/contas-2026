package br.com.rdrtech.faturaimportacao;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.stereotype.Component;

/**
 * Fila própria para a leitura de faturas por IA, que pode levar minutos. Não é exposta como bean
 * {@link Executor} para não substituir o executor padrão do Spring Boot.
 *
 * <p>As tarefas herdam o {@code SecurityContext} de quem as agendou: o filtro de tenant e a
 * auditoria dependem do usuário autenticado, que não existe numa thread nova.
 */
@Component
public class ExecutorImportacaoFatura {

    private static final int THREADS = 2;
    private static final int FILA = 50;

    private final ThreadPoolTaskExecutor pool;
    private final Executor executor;

    public ExecutorImportacaoFatura() {
        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(THREADS);
        pool.setMaxPoolSize(THREADS);
        pool.setQueueCapacity(FILA);
        pool.setThreadNamePrefix("importacao-fatura-");
        pool.setWaitForTasksToCompleteOnShutdown(false);
        pool.initialize();
        executor = new DelegatingSecurityContextExecutor(pool);
    }

    /** @return {@code false} quando a fila está cheia */
    public boolean agendar(Runnable tarefa) {
        try {
            executor.execute(tarefa);
            return true;
        } catch (TaskRejectedException e) {
            return false;
        }
    }

    @PreDestroy
    void encerrar() {
        pool.shutdown();
    }
}
