import { CurrencyPipe, DatePipe, registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, inject, OnInit, signal } from '@angular/core';
import {
  DashboardItem,
  DashboardProjecaoMes,
  DashboardResumo,
  DashboardService,
} from '../../core/dashboard.service';

const MESES = Array.from({ length: 12 }, (_, index) => ({
  valor: index + 1,
  nome: new Intl.DateTimeFormat('pt-BR', { month: 'long', timeZone: 'UTC' })
    .format(new Date(Date.UTC(2020, index, 1))),
}));

registerLocaleData(localePtBr);

interface GraficoBarra {
  mes: number;
  ano: number;
  x: number;
  largura: number;
  centro: number;
  rotuloMes: string;
  rotuloTotal: string;
  topoValor: number;
  descricao: string;
  segmentos: {
    chave: string;
    y: number;
    altura: number;
    cor: string;
    rotulo: string;
    valor: string;
  }[];
}

interface GraficoEixo {
  y: number;
  valor: number;
  rotulo: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './dashboard.html',
  styleUrls: ['../cartoes/cartoes.css', './dashboard.css'],
})
export class Dashboard implements OnInit {
  private readonly service = inject(DashboardService);
  protected readonly meses = MESES;
  protected readonly quantidadesMeses = Array.from({ length: 22 }, (_, index) => index + 3);
  protected readonly quantidadeMeses = signal(12);
  protected readonly mes = signal(new Date().getMonth() + 1);
  protected readonly ano = signal(new Date().getFullYear());
  private readonly competenciaProjecao = signal({
    mes: new Date().getMonth() + 1,
    ano: new Date().getFullYear(),
  });
  protected readonly resumo = signal<DashboardResumo | null>(null);
  protected readonly projecao = signal<DashboardProjecaoMes[]>([]);
  protected readonly projecaoLoading = signal(true);
  protected readonly projecaoError = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly competenciaError = signal<string | null>(null);
  protected readonly detalhesAbertos = signal(false);
  protected readonly detalhesTitulo = signal('');
  protected readonly detalhes = signal<DashboardItem[]>([]);
  protected readonly detalhesLoading = signal(false);
  protected readonly detalhesError = signal<string | null>(null);
  private detalheRequest = 0;
  private projecaoRequest = 0;

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    if (!Number.isInteger(this.mes()) || this.mes() < 1 || this.mes() > 12
      || !Number.isInteger(this.ano()) || this.ano() < 1900 || this.ano() > 9999) {
      this.competenciaError.set('Informe um mês e ano válidos para consultar o dashboard.');
      this.projecaoRequest += 1;
      this.projecaoLoading.set(false);
      return;
    }
    this.competenciaError.set(null);
    this.competenciaProjecao.set({ mes: this.mes(), ano: this.ano() });
    this.loading.set(true);
    this.error.set(null);
    this.carregarProjecao();
    this.service.consultar(this.mes(), this.ano()).subscribe({
      next: (resumo) => {
        this.resumo.set(resumo);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err));
        this.loading.set(false);
      },
    });
  }

  protected atualizarQuantidadeMeses(event: Event): void {
    const quantidade = Number((event.target as HTMLSelectElement).value);
    if (!Number.isInteger(quantidade) || quantidade < 3 || quantidade > 24) {
      return;
    }
    this.quantidadeMeses.set(quantidade);
    this.carregarProjecao();
  }

  protected atualizarMes(event: Event): void {
    this.mes.set(Number((event.target as HTMLSelectElement).value));
  }

  protected atualizarAno(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.ano.set(valor === '' ? Number.NaN : Number(valor));
  }

  protected abrirDetalhes(tipo: 'saidas' | 'buckets' | 'contas-fixas'): void {
    const titulo = tipo === 'saidas'
      ? 'Saídas lançadas no mês'
      : tipo === 'buckets'
        ? 'Reservas dos buckets'
        : 'Contas fixas do mês anterior';
    this.detalheRequest += 1;
    const requestId = this.detalheRequest;
    this.detalhesTitulo.set(titulo);
    this.detalhes.set([]);
    this.detalhesError.set(null);
    this.detalhesLoading.set(true);
    this.detalhesAbertos.set(true);

    const request = tipo === 'saidas'
      ? this.service.listarSaidas(this.mes(), this.ano())
      : tipo === 'buckets'
        ? this.service.listarBuckets()
        : this.service.listarContasFixas(this.mes(), this.ano());
    request.subscribe({
      next: (itens) => {
        if (this.detalheRequest !== requestId) {
          return;
        }
        this.detalhes.set(itens);
        this.detalhesLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (this.detalheRequest !== requestId) {
          return;
        }
        this.detalhesError.set(this.mensagemErro(err));
        this.detalhesLoading.set(false);
      },
    });
  }

  protected fecharDetalhes(): void {
    this.detalheRequest += 1;
    this.detalhesAbertos.set(false);
    this.detalhesLoading.set(false);
  }

  @HostListener('document:keydown.escape')
  protected aoPressionarEscape(): void {
    if (this.detalhesAbertos()) {
      this.fecharDetalhes();
    }
  }

  protected totalDetalhes(): number {
    return this.detalhes().reduce((total, item) => total + Math.round(item.valor * 100), 0) / 100;
  }

  protected larguraGrafico(): number {
    return Math.max(760, 94 + this.projecao().length * 58);
  }

  protected alturaPlotagem(): number {
    return 236;
  }

  protected baseGrafico(): number {
    return 260;
  }

  protected maximoGrafico(): number {
    const maximo = Math.max(0, ...this.projecao().map((item) => item.totalPrevisto));
    if (maximo === 0) {
      return 1;
    }
    const magnitude = 10 ** Math.floor(Math.log10(maximo));
    const fator = maximo / magnitude;
    return magnitude * (fator <= 1 ? 1 : fator <= 2 ? 2 : fator <= 5 ? 5 : 10);
  }

  protected eixosGrafico(): GraficoEixo[] {
    const maximo = this.maximoGrafico();
    return Array.from({ length: 5 }, (_, index) => {
      const valor = maximo * index / 4;
      return {
        valor,
        y: this.baseGrafico() - this.alturaPlotagem() * index / 4,
        rotulo: this.formatarEixo(valor),
      };
    });
  }

  protected barrasGrafico(): GraficoBarra[] {
    const itens = this.projecao();
    const largura = this.larguraGrafico();
    const passo = (largura - 94) / Math.max(itens.length, 1);
    const larguraBarra = Math.min(32, passo * 0.62);
    const maximo = this.maximoGrafico();
    const series = [
      { chave: 'saidas', rotulo: 'Saídas lançadas', campo: 'gastosLancados', cor: '#3278c7' },
      { chave: 'buckets', rotulo: 'Buckets', campo: 'bucketsReservados', cor: '#39a785' },
      { chave: 'contas-fixas', rotulo: 'Contas Fixas', campo: 'contasFixasMesAnterior', cor: '#e9a23b' },
    ] as const;

    return itens.map((item, index) => {
      const centro = 78 + passo * (index + 0.5);
      let alturaAcumulada = 0;
      const segmentos = series.flatMap((serie) => {
        const valor = item[serie.campo];
        const altura = this.alturaPlotagem() * valor / maximo;
        const segmento = valor > 0 ? [{
          chave: serie.chave,
          y: this.baseGrafico() - alturaAcumulada - altura,
          altura,
          cor: serie.cor,
          rotulo: serie.rotulo,
          valor: this.formatarMoeda(valor),
        }] : [];
        alturaAcumulada += altura;
        return segmento;
      });
      const rotuloMes = `${this.nomeMes(item.mes).slice(0, 3)}/${String(item.ano).slice(-2)}`;
      const descricao = `${this.nomeMes(item.mes)} de ${item.ano}: Saídas lançadas ${this.formatarMoeda(item.gastosLancados)}, `
        + `Buckets ${this.formatarMoeda(item.bucketsReservados)}, Contas Fixas ${this.formatarMoeda(item.contasFixasMesAnterior)}, `
        + `total ${this.formatarMoeda(item.totalPrevisto)}.`;
      return {
        mes: item.mes,
        ano: item.ano,
        x: centro - larguraBarra / 2,
        largura: larguraBarra,
        centro,
        rotuloMes,
        rotuloTotal: this.formatarMoedaCompacta(item.totalPrevisto),
        topoValor: this.baseGrafico() - alturaAcumulada - 8,
        descricao,
        segmentos,
      };
    });
  }

  protected formatarMoeda(valor: number): string {
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

  protected carregarProjecao(): void {
    const requestId = ++this.projecaoRequest;
    this.projecaoLoading.set(true);
    this.projecaoError.set(null);
    const competencia = this.competenciaProjecao();
    this.service.consultarProjecao(competencia.mes, competencia.ano, this.quantidadeMeses()).subscribe({
      next: (projecao) => {
        if (requestId !== this.projecaoRequest) {
          return;
        }
        this.projecao.set(projecao);
        this.projecaoLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (requestId !== this.projecaoRequest) {
          return;
        }
        this.projecaoError.set(this.mensagemErro(err));
        this.projecaoLoading.set(false);
      },
    });
  }

  private nomeMes(mes: number): string {
    return MESES[mes - 1]?.nome ?? '';
  }

  private formatarEixo(valor: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      maximumFractionDigits: Math.abs(valor) >= 100 ? 0 : 2,
    }).format(valor);
  }

  private mensagemErro(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
    }
    return typeof err.error?.message === 'string'
      ? err.error.message
      : `Não foi possível carregar o dashboard (erro ${err.status}).`;
  }
}
