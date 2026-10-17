package br.com.rdrtech.presaida;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.faturaimportacao.FaturaImportacao;
import br.com.rdrtech.saida.Saida;
import br.com.rdrtech.subcategoria.Subcategoria;
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

/**
 * Compra lida de uma fatura que ainda não existe no sistema. Só vira {@link Saida} quando o
 * usuário aprova; o {@code primeiroVencimento} já recua até a parcela 1, então a saída criada
 * inclui as parcelas retroativas.
 */
@Entity
@Table(name = "pre_saida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreSaida extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacao_id", nullable = false)
    private FaturaImportacao importacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @Column(nullable = false, length = 120)
    private String descricao;

    @Column(name = "descricao_fatura", length = 120)
    private String descricaoFatura;

    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(name = "valor_por_parcela", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPorParcela;

    @Column(name = "numero_parcela", nullable = false)
    private int numeroParcela;

    @Column(name = "quantidade_parcelas", nullable = false)
    private int quantidadeParcelas;

    /** Competência (yyyyMM) da fatura em que a compra foi lida. */
    @Column(nullable = false)
    private int competencia;

    @Column(name = "primeiro_vencimento", nullable = false)
    private LocalDate primeiroVencimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategoria_id")
    private Subcategoria subcategoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusPreSaida status = StatusPreSaida.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saida_id")
    private Saida saida;

    /** Saída já lançada que provavelmente é esta mesma compra; o usuário confirma ou descarta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saida_sugerida_id")
    private Saida saidaSugerida;

    @Column(name = "motivo_sugestao", length = 500)
    private String motivoSugestao;

    /** Sugestão que o usuário recusou ("é uma compra nova"); o reprocessamento respeita a decisão. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saida_descartada_id")
    private Saida saidaDescartada;

    /** Vinculada, rejeitada ou com sugestão recusada: uma escolha do usuário que prevalece sobre a conciliação. */
    public boolean decididaPeloUsuario() {
        return status == StatusPreSaida.VINCULADA || status == StatusPreSaida.REJEITADA || saidaDescartada != null;
    }
}
