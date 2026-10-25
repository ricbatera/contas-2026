package br.com.rdrtech.ratelimit;

import br.com.rdrtech.api.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita as requisições a {@code /api/**} por IP (token bucket do Bucket4j). Roda antes do Spring
 * Security, então também barra requisições sem token. As rotas de autenticação têm um limite
 * próprio, bem menor, contra força bruta. Os buckets ficam em memória (backend em instância única)
 * e expiram quando o IP fica parado, para o mapa não crescer sem limite.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String MENSAGEM = "Muitas requisições. Tente novamente em instantes.";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration maiorJanela = properties.auth().janela().compareTo(properties.api().janela()) > 0
                ? properties.auth().janela()
                : properties.api().janela();
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(maiorJanela.multipliedBy(2))
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.habilitado() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean rotaAuth = request.getRequestURI().startsWith("/api/auth/");
        RateLimitProperties.Limite limite = rotaAuth ? properties.auth() : properties.api();
        String chave = (rotaAuth ? "auth:" : "api:") + IpCliente.de(request);

        ConsumptionProbe probe = buckets.get(chave, k -> novoBucket(limite)).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long segundos = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(), MENSAGEM, request.getRequestURI()));
    }

    private static Bucket novoBucket(RateLimitProperties.Limite limite) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limite.capacidade())
                        .refillGreedy(limite.capacidade(), limite.janela())
                        .build())
                .build();
    }
}
