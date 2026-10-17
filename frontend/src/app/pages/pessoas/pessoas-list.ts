import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Pessoa, PessoaService } from '../../core/pessoa.service';

@Component({
  selector: 'app-pessoas-list',
  imports: [RouterLink],
  templateUrl: './pessoas-list.html',
  styleUrls: ['../cartoes/cartoes.css', './pessoas.css'],
})
export class PessoasList implements OnInit {
  private readonly service = inject(PessoaService);

  protected readonly pessoas = signal<Pessoa[]>([]);
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
      next: (pessoas) => {
        this.pessoas.set(pessoas);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar as pessoas.'));
        this.loading.set(false);
      },
    });
  }

  protected excluir(pessoa: Pessoa): void {
    if (!window.confirm(`Deseja excluir "${pessoa.nome}"? Essa ação não pode ser desfeita.`)) {
      return;
    }

    this.deletingId.set(pessoa.id);
    this.error.set(null);
    this.service.excluir(pessoa.id).subscribe({
      next: () => {
        this.pessoas.update((items) => items.filter((item) => item.id !== pessoa.id));
        this.deletingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, `Não foi possível excluir "${pessoa.nome}".`));
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
