import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContaBancaria, ContaBancariaService, TipoConta } from '../../core/conta-bancaria.service';

const NOMES_TIPO: Record<TipoConta, string> = {
  CORRENTE: 'Conta corrente',
  POUPANCA: 'Poupança',
  CARTEIRA: 'Carteira',
  INVESTIMENTO: 'Investimento',
  OUTRO: 'Outro',
};

@Component({
  selector: 'app-contas-list',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './contas-list.html',
  styleUrls: ['../cartoes/cartoes.css', './contas.css'],
})
export class ContasList implements OnInit {
  private readonly service = inject(ContaBancariaService);

  protected readonly contas = signal<ContaBancaria[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly deletingId = signal<string | null>(null);

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listar().subscribe({
      next: (contas) => {
        this.contas.set(contas);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar as contas.'));
        this.loading.set(false);
      },
    });
  }

  protected tipoConta(tipo: TipoConta): string {
    return NOMES_TIPO[tipo];
  }

  protected excluir(conta: ContaBancaria): void {
    if (!window.confirm(`Deseja excluir a conta "${conta.nome}"? Essa ação não pode ser desfeita.`)) {
      return;
    }

    this.deletingId.set(conta.id);
    this.error.set(null);
    this.service.excluir(conta.id).subscribe({
      next: () => {
        this.contas.update((contas) => contas.filter((item) => item.id !== conta.id));
        this.deletingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, `Não foi possível excluir a conta "${conta.nome}".`));
        this.deletingId.set(null);
      },
    });
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
