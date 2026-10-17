package br.com.rdrtech.ia;

import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "provedor_ia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvedorIa extends TenantAwareEntity {

    @Column(nullable = false, length = 50)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProvedorIa tipo;

    @Column(nullable = false, length = 100)
    private String modelo;

    @Column(name = "base_url", length = 255)
    private String baseUrl;

    /** API key cifrada por {@link CifradorSegredos}; nunca é devolvida pela API. */
    @Column(name = "api_key_cifrada", columnDefinition = "text")
    private String apiKeyCifrada;

    @Column(precision = 3, scale = 2)
    private BigDecimal temperatura;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "timeout_segundos", nullable = false)
    private int timeoutSegundos;

    @Column(nullable = false)
    private boolean padrao;

    @Column(nullable = false)
    private boolean ativo;
}
