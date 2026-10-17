import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaturaImportacaoLista, FaturaImportacaoService } from '../../core/fatura-importacao.service';
import { ImportacaoMonitorService } from '../../core/importacao-monitor.service';
import { mensagemErro, ROTULO_STATUS, rotuloCompetencia } from './importacao-util';

@Component({
  selector: 'app-fatura-importacoes-list',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './fatura-importacoes-list.html',
  styleUrls: ['../cartoes/cartoes.css', './faturas.css', './importacao.css'],
})
export class FaturaImportacoesList implements OnInit {
  private readonly importacaoService = inject(FaturaImportacaoService);
  private readonly monitor = inject(ImportacaoMonitorService);

  protected readonly importacoes = signal<FaturaImportacaoLista[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rotuloStatus = ROTULO_STATUS;
  protected readonly rotuloCompetencia = rotuloCompetencia;

  constructor() {
    // Recarrega quando o monitor vê uma importação terminar (a primeira execução é ignorada).
    let primeira = true;
    effect(() => {
      this.monitor.versao();
      if (primeira) {
        primeira = false;
        return;
      }
      untracked(() => this.carregar(false));
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(mostrarCarregando = true): void {
    this.loading.set(mostrarCarregando);
    this.error.set(null);
    this.importacaoService.listar().subscribe({
      next: (importacoes) => {
        this.importacoes.set(importacoes);
        importacoes
          .filter((item) => item.status === 'PROCESSANDO')
          .forEach((item) => this.monitor.acompanhar({
            id: item.id,
            nomeArquivo: item.nomeArquivo,
            status: item.status,
            erro: null,
            preSaidasPendentes: item.preSaidasPendentes,
            atualizadoEm: null,
          }));
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErro(err, 'Não foi possível carregar as importações.'));
        this.loading.set(false);
      },
    });
  }
}
