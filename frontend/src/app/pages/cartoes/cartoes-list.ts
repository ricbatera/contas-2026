import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cartao, CartaoService } from '../../core/cartao.service';

@Component({
  selector: 'app-cartoes-list',
  imports: [DatePipe, RouterLink],
  templateUrl: './cartoes-list.html',
  styleUrl: './cartoes.css',
})
export class CartoesList implements OnInit {
  private readonly cartoesService = inject(CartaoService);

  protected readonly cartoes = signal<Cartao[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.cartoesService.listar().subscribe({
      next: (cartoes) => {
        this.cartoes.set(cartoes);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar os cartões.'));
        this.loading.set(false);
      },
    });
  }

  protected nomeBandeira(bandeira: string): string {
    return bandeira === 'OUTRO' ? 'Outra bandeira' : bandeira;
  }

  protected nomePrincipal(id: string | null): string | null {
    if (!id) {
      return null;
    }
    const principal = this.cartoes().find((cartao) => cartao.id === id);
    return principal ? principal.nome : null;
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
