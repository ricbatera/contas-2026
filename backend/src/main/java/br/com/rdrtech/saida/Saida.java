package br.com.rdrtech.saida;

import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.tenant.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "saida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Saida extends TenantAwareEntity {

    @Column(nullable = false, length = 120)
    private String descricao;

    @Column(name = "descricao_fatura", length = 120)
    private String descricaoFatura;

    @Column(name = "conta_fixa", nullable = false)
    private boolean contaFixa;

    @Column(name = "valor_por_parcela", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPorParcela;

    @Column(name = "quantidade_parcelas", nullable = false)
    private int quantidadeParcelas;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_responsabilidade", nullable = false, length = 20)
    private ModoResponsabilidade modoResponsabilidade;

    @Column(name = "data_inicial")
    private LocalDate dataInicial;

    @Column(name = "primeiro_vencimento")
    private LocalDate primeiroVencimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcategoria_id", nullable = false)
    private Subcategoria subcategoria;

    @OneToMany(mappedBy = "saida", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaidaParcela> parcelas = new ArrayList<>();

    @OneToMany(mappedBy = "saida", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaidaResponsavel> responsabilidades = new ArrayList<>();
}
