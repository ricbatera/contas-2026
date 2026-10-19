import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EntradaOcorrencia, EntradaService, StatusOcorrencia } from '../../core/entrada.service';
import { CampoCalculadora } from '../../shared/calculadora/campo-calculadora';

interface FiltrosEntrada {
  texto: string;
  categoriaId: string;
  subcategoriaId: string;
  contaBancariaId: string;
  status: string;
  recorrente: string;
}

interface OpcaoFiltro {
  id: string;
  nome: string;
}

const filtrosVazios = (): FiltrosEntrada => ({
  texto: '',
  categoriaId: '',
  subcategoriaId: '',
  contaBancariaId: '',
  status: '',
  recorrente: '',
});

const hojeIso = (): string => {
  const hoje = new Date();
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
};

const centavos = (valor: number): number => Math.round(valor * 100);

@Component({
  selector: 'app-entradas-list',
  imports: [CampoCalculadora, CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './entradas-list.html',
  styleUrls: ['../cartoes/cartoes.css', '../saidas/saidas.css', './entradas.css'],
})
export class EntradasList implements OnInit {
  private readonly service = inject(EntradaService);

  protected readonly competencia = signal({ mes: new Date().getMonth() + 1, ano: new Date().getFullYear() });
  protected readonly rotuloCompetencia = computed(() => {
    const { mes, ano } = this.competencia();
    return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' })
      .format(new Date(Date.UTC(ano, mes - 1, 1)));
  });
  protected readonly ocorrencias = signal<EntradaOcorrencia[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly workingId = signal<string | null>(null);
  protected readonly filtrosAbertos = signal(false);
  protected readonly filtrosRascunho = signal<FiltrosEntrada>(filtrosVazios());
  protected readonly filtrosAplicados = signal<FiltrosEntrada>(filtrosVazios());
  protected readonly quantidadeFiltrosAtivos = computed(() =>
    Object.values(this.filtrosAplicados()).filter(Boolean).length,
  );
  protected readonly ocorrenciasFiltradas = computed(() => {
    const filtros = this.filtrosAplicados();
    const texto = filtros.texto.trim().toLocaleLowerCase('pt-BR');
    return this.ocorrencias().filter((ocorrencia) =>
      (!texto || ocorrencia.descricao.toLocaleLowerCase('pt-BR').includes(texto))
      && (!filtros.categoriaId || ocorrencia.categoriaId === filtros.categoriaId)
      && (!filtros.subcategoriaId || ocorrencia.subcategoriaId === filtros.subcategoriaId)
      && (!filtros.contaBancariaId || ocorrencia.contaBancariaId === filtros.contaBancariaId)
      && (!filtros.status || ocorrencia.status === filtros.status)
      && (filtros.recorrente !== 'SIM' || ocorrencia.recorrente)
      && (filtros.recorrente !== 'NAO' || !ocorrencia.recorrente),
    );
  });
  /** Esperado no mês: o valor recebido quando houver, senão o previsto; canceladas não contam. */
  protected readonly totalPrevisto = computed(() =>
    this.ocorrenciasFiltradas()
      .filter((ocorrencia) => ocorrencia.status !== 'CANCELADA')
      .reduce((total, ocorrencia) => total + centavos(this.valorEsperado(ocorrencia)), 0) / 100,
  );
  protected readonly totalRecebido = computed(() =>
    this.ocorrenciasFiltradas()
      .filter((ocorrencia) => ocorrencia.status === 'RECEBIDA')
      .reduce((total, ocorrencia) => total + centavos(ocorrencia.valorRecebido ?? 0), 0) / 100,
  );
  protected readonly categoriasDisponiveis = computed(() =>
    this.opcoesUnicas(this.ocorrencias().map((item) => ({ id: item.categoriaId, nome: item.categoriaNome }))),
  );
  protected readonly subcategoriasDisponiveis = computed(() => {
    const categoriaId = this.filtrosRascunho().categoriaId;
    return this.opcoesUnicas(
      this.ocorrencias()
        .filter((item) => !categoriaId || item.categoriaId === categoriaId)
        .map((item) => ({ id: item.subcategoriaId, nome: item.subcategoriaNome })),
    );
  });
  protected readonly contasDisponiveis = computed(() =>
    this.opcoesUnicas(
      this.ocorrencias()
        .filter((item) => item.contaBancariaId && item.contaBancariaNome)
        .map((item) => ({ id: item.contaBancariaId!, nome: item.contaBancariaNome! })),
    ),
  );

  protected readonly recebendo = signal<EntradaOcorrencia | null>(null);
  protected readonly erroRecebimento = signal<string | null>(null);
  protected readonly formRecebimento = inject(FormBuilder).nonNullable.group({
    valorRecebido: [0, [Validators.required, Validators.min(0.01)]],
    dataRecebimento: ['', Validators.required],
  });

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    const { mes, ano } = this.competencia();
    this.loading.set(true);
    this.error.set(null);
    this.service.listarOcorrencias(mes, ano).subscribe({
      next: (ocorrencias) => {
        if (mes !== this.competencia().mes || ano !== this.competencia().ano) {
          return;
        }
        this.ocorrencias.set(ocorrencias);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar as entradas.'));
        this.loading.set(false);
      },
    });
  }

  protected mudarMes(deslocamento: number): void {
    const { mes, ano } = this.competencia();
    const data = new Date(Date.UTC(ano, mes - 1 + deslocamento, 1));
    this.competencia.set({ mes: data.getUTCMonth() + 1, ano: data.getUTCFullYear() });
    this.carregar();
  }

  protected valorEsperado(ocorrencia: EntradaOcorrencia): number {
    return ocorrencia.status === 'RECEBIDA' ? ocorrencia.valorRecebido ?? 0 : ocorrencia.valorPrevisto;
  }

  protected nomeStatus(status: StatusOcorrencia): string {
    return { PREVISTA: 'Prevista', RECEBIDA: 'Recebida', CANCELADA: 'Cancelada' }[status];
  }

  protected abrirFiltros(): void {
    this.filtrosRascunho.set({ ...this.filtrosAplicados() });
    this.filtrosAbertos.set(true);
  }

  protected atualizarFiltro(campo: keyof FiltrosEntrada, event: Event): void {
    const valor = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.filtrosRascunho.update((filtros) => ({
      ...filtros,
      [campo]: valor,
      ...(campo === 'categoriaId' ? { subcategoriaId: '' } : {}),
    }));
  }

  protected aplicarFiltros(): void {
    this.filtrosAplicados.set({ ...this.filtrosRascunho() });
    this.filtrosAbertos.set(false);
  }

  protected limparFiltros(): void {
    this.filtrosRascunho.set(filtrosVazios());
    this.filtrosAplicados.set(filtrosVazios());
  }

  protected cancelarFiltros(): void {
    this.filtrosRascunho.set({ ...this.filtrosAplicados() });
    this.filtrosAbertos.set(false);
  }

  protected abrirRecebimento(ocorrencia: EntradaOcorrencia): void {
    this.formRecebimento.reset({
      valorRecebido: ocorrencia.valorRecebido ?? ocorrencia.valorPrevisto,
      dataRecebimento: ocorrencia.dataRecebimento ?? hojeIso(),
    });
    this.erroRecebimento.set(null);
    this.recebendo.set(ocorrencia);
  }

  protected fecharRecebimento(): void {
    this.recebendo.set(null);
  }

  protected confirmarRecebimento(): void {
    const ocorrencia = this.recebendo();
    if (!ocorrencia || this.formRecebimento.invalid || this.workingId()) {
      this.formRecebimento.markAllAsTouched();
      return;
    }
    const value = this.formRecebimento.getRawValue();
    this.workingId.set(ocorrencia.id);
    this.erroRecebimento.set(null);
    this.service
      .receber(ocorrencia.id, { valorRecebido: Number(value.valorRecebido), dataRecebimento: value.dataRecebimento })
      .subscribe({
        next: (atualizada) => {
          this.substituir(atualizada);
          this.workingId.set(null);
          this.recebendo.set(null);
        },
        error: (err: HttpErrorResponse) => {
          this.erroRecebimento.set(this.mensagemErro(err, 'Não foi possível registrar o recebimento.'));
          this.workingId.set(null);
        },
      });
  }

  protected cancelar(ocorrencia: EntradaOcorrencia): void {
    this.executar(ocorrencia, this.service.cancelar(ocorrencia.id), 'Não foi possível cancelar a ocorrência.');
  }

  protected reabrir(ocorrencia: EntradaOcorrencia): void {
    this.executar(ocorrencia, this.service.reabrir(ocorrencia.id), 'Não foi possível reabrir a ocorrência.');
  }

  protected excluir(ocorrencia: EntradaOcorrencia): void {
    const mensagem = ocorrencia.recorrente
      ? `Deseja excluir "${ocorrencia.descricao}" e todas as ocorrências de todos os meses, inclusive as recebidas? `
        + 'Para encerrar a recorrência mantendo o histórico, edite a entrada e informe uma data final.'
      : `Deseja excluir "${ocorrencia.descricao}"? Essa ação não pode ser desfeita.`;
    if (!window.confirm(mensagem)) {
      return;
    }
    this.workingId.set(ocorrencia.id);
    this.error.set(null);
    this.service.excluir(ocorrencia.entradaId).subscribe({
      next: () => {
        this.ocorrencias.update((items) => items.filter((item) => item.entradaId !== ocorrencia.entradaId));
        this.workingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, `Não foi possível excluir "${ocorrencia.descricao}".`));
        this.workingId.set(null);
      },
    });
  }

  private executar(
    ocorrencia: EntradaOcorrencia,
    operacao: ReturnType<EntradaService['cancelar']>,
    fallback: string,
  ): void {
    this.workingId.set(ocorrencia.id);
    this.error.set(null);
    operacao.subscribe({
      next: (atualizada) => {
        this.substituir(atualizada);
        this.workingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, fallback));
        this.workingId.set(null);
      },
    });
  }

  private substituir(atualizada: EntradaOcorrencia): void {
    this.ocorrencias.update((items) => items.map((item) => (item.id === atualizada.id ? atualizada : item)));
  }

  private opcoesUnicas(opcoes: OpcaoFiltro[]): OpcaoFiltro[] {
    return [...new Map(opcoes.map((opcao) => [opcao.id, opcao])).values()]
      .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
  }

  private mensagemErro(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
    }
    return typeof err.error?.message === 'string'
      ? err.error.message
      : `${fallback} (erro ${err.status}).`;
  }
}
