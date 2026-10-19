package br.com.rdrtech.comprovante;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.categoria.Categoria;
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
 * Foto de um comprovante de maquininha lida pela IA. Os campos editáveis começam com o que a IA
 * leu e só viram {@link Saida} quando o usuário aprova. A saída nasce sem {@code descricaoFatura}:
 * o texto da fatura chega depois, pela conciliação da importação do PDF.
 */
@Entity
@Table(name = "comprovante_cartao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprovanteCartao extends TenantAwareEntity {

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(nullable = false)
    private byte[] imagem;

    @Column(name = "tipo_midia", nullable = false, length = 30)
    private String tipoMidia;

    @Column(name = "tamanho_bytes", nullable = false)
    private int tamanhoBytes;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusComprovante status = StatusComprovante.PENDENTE;

    /** Falha da leitura por IA; o usuário pode preencher à mão ou reler. */
    @Column(name = "erro_leitura", columnDefinition = "text")
    private String erroLeitura;

    /** Avisos da leitura, um por linha. */
    @Column(columnDefinition = "text")
    private String avisos;

    @Column(length = 120)
    private String estabelecimento;

    @Column(name = "ultimos_digitos_lidos", length = 4)
    private String ultimosDigitosLidos;

    @Column(name = "bandeira_lida", length = 20)
    private String bandeiraLida;

    @Column(name = "resposta_ia", columnDefinition = "text")
    private String respostaIa;

    @Column(name = "provedor_modelo", length = 100)
    private String provedorModelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ModalidadeComprovante modalidade = ModalidadeComprovante.CREDITO;

    @Column(length = 120)
    private String descricao;

    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(name = "valor_total", precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "quantidade_parcelas", nullable = false)
    @Builder.Default
    private int quantidadeParcelas = 1;

    @Column(name = "primeiro_vencimento")
    private LocalDate primeiroVencimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategoria_id")
    private Subcategoria subcategoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saida_id")
    private Saida saida;
}
