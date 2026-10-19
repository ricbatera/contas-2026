import { CurrencyPipe, registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DashboardProjecaoMes, DashboardService } from '../../core/dashboard.service';
import { CampoCalculadora } from '../../shared/calculadora/campo-calculadora';

registerLocaleData(localePtBr);

interface SimulacaoSaida {
  id: string;
  nome: string;
  valorPorParcela: number;
  quantidadeParcelas: number;
}

interface MesSimulado extends DashboardProjecaoMes {
  valorSimulado: number;
  totalComSimulacao: number;
}

const STORAGE_KEY = 'simulacao-orcamento-v1';
const MESES_PARA_EXIBIR = Array.from({ length: 22 }, (_, index) => index + 3);
const QUANTIDADES_PARCELAS = Array.from({ length: 60 }, (_, index) => index + 1);

@Component({
  selector: 'app-simulacao',
  imports: [CampoCalculadora, CurrencyPipe, ReactiveFormsModule],
  templateUrl: './simulacao.html',
  styleUrls: ['../cartoes/cartoes.css', './simulacao.css'],
})
export class Simulacao implements OnInit {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly service = inject(DashboardService);
  private requisicao = 0;

  protected readonly mesesDisponiveis = MESES_PARA_EXIBIR;
  protected readonly quantidadesParcelas = QUANTIDADES_PARCELAS;
  protected readonly quantidadeMeses = signal(12);
  protected readonly simulacoes = signal<SimulacaoSaida[]>([]);
  protected readonly projecao = signal<DashboardProjecaoMes[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly storageError = signal<string | null>(null);
  protected readonly storageCorrupt = signal(false);
  protected readonly storageReady = signal(false);
  protected readonly formulario = this.fb.group({
    nome: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    valorPorParcela: [0, [Validators.required, Validators.min(0.01)]],
    quantidadeParcelas: [1, [Validators.required, Validators.min(1), Validators.max(60)]],
  });

  protected readonly mesesSimulados = computed<MesSimulado[]>(() =>
    this.projecao().map((mes, index) => {
      const valorSimuladoEmCentavos = this.simulacoes()
        .filter((item) => index < item.quantidadeParcelas)
        .reduce((total, item) => total + Math.round(item.valorPorParcela * 100), 0);
      const valorSimulado = valorSimuladoEmCentavos / 100;
      const totalComSimulacao = (Math.round(mes.totalPrevisto * 100) + valorSimuladoEmCentavos) / 100;
      return { ...mes, valorSimulado, totalComSimulacao };
    }),
  );
  protected readonly maiorTotal = computed(() =>
    Math.max(1, ...this.mesesSimulados().map((mes) => mes.totalComSimulacao)),
  );

  ngOnInit(): void {
    this.carregarSimulacoes();
    this.carregarProjecao();
  }

  protected atualizarQuantidadeMeses(event: Event): void {
    const quantidade = Number((event.target as HTMLSelectElement).value);
    if (!Number.isInteger(quantidade) || quantidade < 3 || quantidade > 24) {
      return;
    }
    this.quantidadeMeses.set(quantidade);
    this.carregarProjecao();
  }

  protected adicionar(): void {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || !this.storageReady()) {
      return;
    }

    const value = this.formulario.getRawValue();
    const simulacao: SimulacaoSaida = {
      id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`,
      nome: value.nome.trim(),
      valorPorParcela: Number(value.valorPorParcela),
      quantidadeParcelas: Number(value.quantidadeParcelas),
    };
    if (!simulacao.nome || !Number.isFinite(simulacao.valorPorParcela)
      || simulacao.valorPorParcela <= 0 || !Number.isInteger(simulacao.quantidadeParcelas)
      || simulacao.quantidadeParcelas < 1 || simulacao.quantidadeParcelas > 60) {
      this.formulario.markAllAsTouched();
      return;
    }

    if (this.salvarSimulacoes([...this.simulacoes(), simulacao])) {
      this.formulario.reset({ nome: '', valorPorParcela: 0, quantidadeParcelas: 1 });
    }
  }

  protected remover(id: string): void {
    this.salvarSimulacoes(this.simulacoes().filter((item) => item.id !== id));
  }

  protected descartarDadosInvalidos(): void {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      this.storageError.set('Não foi possível remover os dados inválidos do armazenamento local.');
      return;
    }
    this.simulacoes.set([]);
    this.storageCorrupt.set(false);
    this.storageError.set(null);
    this.storageReady.set(true);
  }

  protected recarregar(): void {
    this.carregarProjecao();
  }

  protected larguraBase(mes: MesSimulado): number {
    return Math.min((mes.totalPrevisto / this.maiorTotal()) * 100, 100);
  }

  protected larguraSimulada(mes: MesSimulado): number {
    return Math.min((mes.valorSimulado / this.maiorTotal()) * 100, 100);
  }

  protected rotuloMes(mes: DashboardProjecaoMes): string {
    return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' })
      .format(new Date(Date.UTC(mes.ano, mes.mes - 1, 1)));
  }

  protected mensagemErro(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
    }
    return typeof err.error?.message === 'string'
      ? err.error.message
      : `Não foi possível carregar a previsão dos gastos (erro ${err.status}).`;
  }

  private carregarProjecao(): void {
    const requisicaoAtual = ++this.requisicao;
    this.loading.set(true);
    this.error.set(null);
    const agora = new Date();
    this.service.consultarProjecao(
      agora.getMonth() + 1,
      agora.getFullYear(),
      this.quantidadeMeses(),
    ).subscribe({
      next: (projecao) => {
        if (requisicaoAtual !== this.requisicao) {
          return;
        }
        this.projecao.set(projecao);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (requisicaoAtual !== this.requisicao) {
          return;
        }
        this.error.set(this.mensagemErro(err));
        this.loading.set(false);
      },
    });
  }

  private carregarSimulacoes(): void {
    let valorSalvo: string | null;
    try {
      valorSalvo = localStorage.getItem(STORAGE_KEY);
    } catch {
      this.storageError.set('O navegador não permitiu acessar as simulações salvas neste dispositivo.');
      return;
    }

    if (valorSalvo === null) {
      this.storageReady.set(true);
      return;
    }

    let dados: unknown;
    try {
      dados = JSON.parse(valorSalvo);
    } catch {
      this.storageCorrupt.set(true);
      this.storageError.set('As simulações salvas estão inválidas. Remova os dados locais da aplicação para recomeçar.');
      return;
    }
    if (!Array.isArray(dados) || !dados.every((item) => this.ehSimulacaoValida(item))) {
      this.storageCorrupt.set(true);
      this.storageError.set('As simulações salvas estão inválidas. Remova os dados locais da aplicação para recomeçar.');
      return;
    }

    this.simulacoes.set(dados);
    this.storageReady.set(true);
  }

  private salvarSimulacoes(simulacoes: SimulacaoSaida[]): boolean {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(simulacoes));
    } catch {
      this.storageError.set('Não foi possível salvar as simulações no armazenamento local do navegador.');
      return false;
    }
    this.simulacoes.set(simulacoes);
    this.storageError.set(null);
    this.storageCorrupt.set(false);
    return true;
  }

  private ehSimulacaoValida(value: unknown): value is SimulacaoSaida {
    if (typeof value !== 'object' || value === null || !('id' in value)
      || !('nome' in value) || !('valorPorParcela' in value) || !('quantidadeParcelas' in value)) {
      return false;
    }
    return typeof value.id === 'string'
      && typeof value.nome === 'string'
      && value.nome.trim().length > 0
      && typeof value.valorPorParcela === 'number'
      && Number.isFinite(value.valorPorParcela)
      && value.valorPorParcela > 0
      && typeof value.quantidadeParcelas === 'number'
      && Number.isInteger(value.quantidadeParcelas)
      && value.quantidadeParcelas >= 1
      && value.quantidadeParcelas <= 60;
  }
}
