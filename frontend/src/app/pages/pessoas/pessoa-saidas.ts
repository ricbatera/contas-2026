import { CurrencyPipe, DatePipe, registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Pessoa } from '../../core/pessoa.service';
import { FormaPagamento, Saida, SaidaParcela, SaidaService } from '../../core/saida.service';
import { correspondeBusca } from '../../shared/busca/busca';
import { CampoBusca } from '../../shared/busca/campo-busca';
import { mesReferencia } from '../../shared/data/mes-referencia';

/** Parcela de uma saída que vence no mês, com a parte da pessoa. */
interface ParcelaDaPessoa {
  saida: Saida;
  parcela: SaidaParcela;
  valor: number;
}

interface BarraMes {
  chave: string;
  x: number;
  y: number;
  largura: number;
  altura: number;
  centro: number;
  rotuloMes: string;
  rotuloTotal: string;
  descricao: string;
}

const NOMES_FORMA: Record<FormaPagamento, string> = {
  CARTAO_CREDITO: 'Cartão de crédito',
  BOLETO: 'Boleto',
  DEBITO: 'Débito',
  PIX: 'Pix',
};

registerLocaleData(localePtBr);

const LARGURA_MINIMA = 760;
const ALTURA_PLOTAGEM = 236;
const BASE = 260;

/**
 * Saídas de uma pessoa: as parcelas que vencem no mês escolhido, com a parte dela, e o total
 * mensal dos meses seguintes. Usa a mesma regra do dashboard: a parte do responsável em cada
 * parcela, no mês do vencimento da parcela.
 */
@Component({
  selector: 'app-pessoa-saidas',
  imports: [CampoBusca, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './pessoa-saidas.html',
  styleUrls: [
    '../cartoes/cartoes.css',
    '../entradas/entradas.css',
    '../faturas/faturas.css',
    '../dashboard/dashboard.css',
    './pessoa-saidas.css',
  ],
})
export class PessoaSaidas implements OnInit {
  private readonly service = inject(SaidaService);

  readonly pessoas = input.required<Pessoa[]>();

  protected readonly quantidadesMeses = Array.from({ length: 22 }, (_, index) => index + 3);
  protected readonly quantidadeMeses = signal(12);
  protected readonly pessoaId = signal('');
  protected readonly competencia = signal(mesReferencia());
  protected readonly saidas = signal<Saida[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busca = signal('');

  protected readonly pessoasOrdenadas = computed(() =>
    [...this.pessoas()].sort((a, b) => Number(b.ativo) - Number(a.ativo) || a.nome.localeCompare(b.nome, 'pt-BR')));
  protected readonly nomePessoa = computed(() =>
    this.pessoas().find((pessoa) => pessoa.id === this.pessoaId())?.nome ?? '');
  protected readonly rotuloCompetencia = computed(() => {
    const { mes, ano } = this.competencia();
    return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' })
      .format(new Date(Date.UTC(ano, mes - 1, 1)));
  });

  /** Todas as parcelas em que a pessoa tem parte, com o valor dela. */
  private readonly parcelasDaPessoa = computed<ParcelaDaPessoa[]>(() => {
    const pessoaId = this.pessoaId();
    if (!pessoaId) {
      return [];
    }
    return this.saidas().flatMap((saida) => {
      const parte = saida.responsabilidades.find((responsavel) => responsavel.pessoaId === pessoaId);
      return parte ? saida.parcelas.map((parcela) => ({ saida, parcela, valor: parte.valorPorParcela })) : [];
    });
  });
  protected readonly itensDoMes = computed(() => {
    const prefixo = this.prefixoMes(this.competencia().mes, this.competencia().ano);
    return this.parcelasDaPessoa()
      .filter((item) => item.parcela.vencimento.startsWith(prefixo))
      .sort((a, b) => a.parcela.vencimento.localeCompare(b.parcela.vencimento)
        || a.saida.descricao.localeCompare(b.saida.descricao, 'pt-BR'));
  });
  protected readonly itensEncontrados = computed(() =>
    this.itensDoMes().filter(({ saida, parcela, valor }) => correspondeBusca(
      this.busca(),
      saida.descricao,
      saida.descricaoFatura,
      saida.categoriaNome,
      saida.subcategoriaNome,
      saida.cartaoNome,
      this.nomeForma(saida.formaPagamento),
      saida.contaFixa ? 'Conta fixa' : null,
      `${parcela.numero}/${saida.quantidadeParcelas}`,
      valor,
    )));
  protected readonly totalDoMes = computed(() => this.somar(this.itensEncontrados().map((item) => item.valor)));

  /** Total da pessoa em cada mês, a partir do mês escolhido. */
  protected readonly progressao = computed(() => {
    const totais = new Map<string, number>();
    for (const { parcela, valor } of this.parcelasDaPessoa()) {
      const chave = parcela.vencimento.slice(0, 7);
      totais.set(chave, (totais.get(chave) ?? 0) + Math.round(valor * 100));
    }
    const { mes, ano } = this.competencia();
    return Array.from({ length: this.quantidadeMeses() }, (_, indice) => {
      const data = new Date(Date.UTC(ano, mes - 1 + indice, 1));
      const mesItem = data.getUTCMonth() + 1;
      const anoItem = data.getUTCFullYear();
      const chave = `${anoItem}-${String(mesItem).padStart(2, '0')}`;
      return { mes: mesItem, ano: anoItem, chave, total: (totais.get(chave) ?? 0) / 100 };
    });
  });
  protected readonly larguraGrafico = computed(() => Math.max(LARGURA_MINIMA, 94 + this.progressao().length * 58));
  protected readonly maximoGrafico = computed(() => {
    const maximo = Math.max(0, ...this.progressao().map((item) => item.total));
    if (maximo === 0) {
      return 1;
    }
    const magnitude = 10 ** Math.floor(Math.log10(maximo));
    const fator = maximo / magnitude;
    return magnitude * (fator <= 1 ? 1 : fator <= 2 ? 2 : fator <= 5 ? 5 : 10);
  });
  protected readonly eixosGrafico = computed(() =>
    Array.from({ length: 5 }, (_, indice) => {
      const valor = this.maximoGrafico() * indice / 4;
      return { valor, y: BASE - ALTURA_PLOTAGEM * indice / 4, rotulo: this.formatarEixo(valor) };
    }));
  protected readonly barrasGrafico = computed<BarraMes[]>(() => {
    const itens = this.progressao();
    const passo = (this.larguraGrafico() - 94) / Math.max(itens.length, 1);
    const largura = Math.min(32, passo * 0.62);
    const nome = this.nomePessoa();
    return itens.map((item, indice) => {
      const centro = 78 + passo * (indice + 0.5);
      const altura = ALTURA_PLOTAGEM * item.total / this.maximoGrafico();
      const nomeMes = new Intl.DateTimeFormat('pt-BR', { month: 'long', timeZone: 'UTC' })
        .format(new Date(Date.UTC(item.ano, item.mes - 1, 1)));
      return {
        chave: item.chave,
        x: centro - largura / 2,
        y: BASE - altura,
        largura,
        altura,
        centro,
        rotuloMes: `${nomeMes.slice(0, 3)}/${String(item.ano).slice(-2)}`,
        rotuloTotal: this.formatarMoedaCompacta(item.total),
        descricao: `${nomeMes} de ${item.ano}: ${nome} ${this.formatarMoeda(item.total)}.`,
      };
    });
  });

  constructor() {
    // Seleciona a primeira pessoa quando a lista chega ou quando a selecionada some (excluída).
    effect(() => {
      const pessoas = this.pessoasOrdenadas();
      if (!pessoas.some((pessoa) => pessoa.id === this.pessoaId())) {
        this.pessoaId.set(pessoas[0]?.id ?? '');
      }
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listar().subscribe({
      next: (saidas) => {
        this.saidas.set(saidas);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 0
          ? 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.'
          : typeof err.error?.message === 'string'
            ? err.error.message
            : `Não foi possível carregar as saídas (erro ${err.status}).`);
        this.loading.set(false);
      },
    });
  }

  protected selecionarPessoa(event: Event): void {
    this.pessoaId.set((event.target as HTMLSelectElement).value);
  }

  protected mudarMes(delta: number): void {
    const { mes, ano } = this.competencia();
    const data = new Date(Date.UTC(ano, mes - 1 + delta, 1));
    this.competencia.set({ mes: data.getUTCMonth() + 1, ano: data.getUTCFullYear() });
  }

  protected atualizarQuantidadeMeses(event: Event): void {
    this.quantidadeMeses.set(Number((event.target as HTMLSelectElement).value));
  }

  protected nomeForma(forma: FormaPagamento): string {
    return NOMES_FORMA[forma];
  }

  private prefixoMes(mes: number, ano: number): string {
    return `${ano}-${String(mes).padStart(2, '0')}-`;
  }

  private somar(valores: number[]): number {
    return valores.reduce((total, valor) => total + Math.round(valor * 100), 0) / 100;
  }

  private formatarMoeda(valor: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor);
  }

  private formatarMoedaCompacta(valor: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(valor);
  }

  private formatarEixo(valor: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      maximumFractionDigits: Math.abs(valor) >= 100 ? 0 : 2,
    }).format(valor);
  }
}
