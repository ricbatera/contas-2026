package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PDF de fatura enviado pelo usuário e o resultado da leitura por IA. */
@Entity
@Table(name = "fatura_importacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaturaImportacao extends TenantAwareEntity {

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    /** PDF já sem senha (a proteção é removida no upload). */
    @Column(nullable = false)
    private byte[] pdf;

    /** Hash do arquivo enviado, para avisar quando a mesma fatura é importada de novo. */
    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "tamanho_bytes", nullable = false)
    private int tamanhoBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_principal_id")
    private Cartao cartaoPrincipal;

    private Integer competencia;

    private LocalDate vencimento;

    @Column(name = "total_pdf", precision = 12, scale = 2)
    private BigDecimal totalPdf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusImportacao status;

    @Column(columnDefinition = "text")
    private String erro;

    /** Resposta bruta da IA, guardada para auditoria. */
    @Column(name = "resposta_ia", columnDefinition = "text")
    private String respostaIa;

    /** JSON de {@link br.com.rdrtech.faturaimportacao.dto.ResumoImportacao}. */
    @Column(columnDefinition = "text")
    private String resumo;

    @Column(name = "provedor_modelo", length = 100)
    private String provedorModelo;
}
