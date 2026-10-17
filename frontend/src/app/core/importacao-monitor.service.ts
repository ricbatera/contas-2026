import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, forkJoin, map, of } from 'rxjs';
import { FaturaImportacaoService, FaturaImportacaoStatus } from './fatura-importacao.service';

export interface AlertaImportacao {
  id: string;
  nomeArquivo: string;
  sucesso: boolean;
  preSaidasPendentes: number;
  erro: string | null;
}

/**
 * Acompanha as importações de fatura que estão sendo lidas pela IA no servidor. Enquanto houver
 * alguma em PROCESSANDO, consulta o status a cada {@link INTERVALO_MS}; quando termina, gera um
 * alerta (exceto se o usuário já estiver na tela de revisão daquela importação).
 */
@Injectable({ providedIn: 'root' })
export class ImportacaoMonitorService {
  static readonly INTERVALO_MS = 3000;

  private readonly importacaoService = inject(FaturaImportacaoService);

  private readonly estados = signal<Record<string, FaturaImportacaoStatus>>({});
  readonly alertas = signal<AlertaImportacao[]>([]);
  /** Importação aberta na tela de revisão, que se atualiza sozinha e dispensa o alerta. */
  readonly visualizando = signal<string | null>(null);
  /** Muda a cada importação concluída, para listas abertas recarregarem. */
  readonly versao = signal(0);
  readonly emProcessamento = computed(() =>
    Object.values(this.estados()).filter((estado) => estado.status === 'PROCESSANDO'));

  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private consultando = false;
  private ativo = false;

  /** Retoma o acompanhamento das importações que ainda estão processando (ex.: após recarregar a página). */
  iniciar(): void {
    this.ativo = true;
    this.importacaoService.listar().subscribe({
      next: (importacoes) => importacoes
        .filter((item) => item.status === 'PROCESSANDO')
        .forEach((item) => this.acompanhar({
          id: item.id,
          nomeArquivo: item.nomeArquivo,
          status: item.status,
          erro: null,
          preSaidasPendentes: item.preSaidasPendentes,
          atualizadoEm: null,
        })),
      error: () => undefined,
    });
  }

  parar(): void {
    this.ativo = false;
    this.cancelarTemporizador();
    this.estados.set({});
    this.alertas.set([]);
    this.visualizando.set(null);
  }

  statusDe(id: string): FaturaImportacaoStatus | undefined {
    return this.estados()[id];
  }

  acompanhar(estado: FaturaImportacaoStatus): void {
    this.estados.update((estados) => ({ ...estados, [estado.id]: estado }));
    if (estado.status === 'PROCESSANDO') {
      this.alertas.update((alertas) => alertas.filter((alerta) => alerta.id !== estado.id));
      this.agendar();
    }
  }

  dispensar(id: string): void {
    this.alertas.update((alertas) => alertas.filter((alerta) => alerta.id !== id));
  }

  private agendar(): void {
    if (!this.ativo || this.temporizador !== null || this.consultando) {
      return;
    }
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.consultar();
    }, ImportacaoMonitorService.INTERVALO_MS);
  }

  private consultar(): void {
    const pendentes = this.emProcessamento();
    if (!pendentes.length || !this.ativo) {
      return;
    }
    this.consultando = true;
    forkJoin(pendentes.map((estado) => this.importacaoService.buscarStatus(estado.id).pipe(
      // 404: a importação foi excluída; outros erros: tenta de novo na próxima rodada.
      catchError((err: HttpErrorResponse) => of(err.status === 404 ? null : estado)),
      map((atual) => ({ anterior: estado, atual })),
    ))).subscribe((resultados) => {
      this.consultando = false;
      if (!this.ativo) {
        return;
      }
      const proximos = { ...this.estados() };
      const concluidas: FaturaImportacaoStatus[] = [];
      for (const { anterior, atual } of resultados) {
        if (atual === null) {
          delete proximos[anterior.id];
        } else {
          proximos[atual.id] = atual;
          if (atual.status !== 'PROCESSANDO') {
            concluidas.push(atual);
          }
        }
      }
      this.estados.set(proximos);
      concluidas.forEach((estado) => this.alertar(estado));
      if (concluidas.length) {
        this.versao.update((versao) => versao + 1);
      }
      this.agendar();
    });
  }

  private alertar(estado: FaturaImportacaoStatus): void {
    if (this.visualizando() === estado.id) {
      return;
    }
    this.alertas.update((alertas) => [
      ...alertas.filter((alerta) => alerta.id !== estado.id),
      {
        id: estado.id,
        nomeArquivo: estado.nomeArquivo,
        sucesso: estado.status === 'CONCLUIDA',
        preSaidasPendentes: estado.preSaidasPendentes,
        erro: estado.erro,
      },
    ]);
  }

  private cancelarTemporizador(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
