import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ComprovanteLista, ComprovanteService } from '../../core/comprovante.service';
import { mensagemErro } from '../faturas/importacao-util';
import { ROTULO_STATUS_COMPROVANTE, rotuloModalidade } from './comprovantes-util';

@Component({
  selector: 'app-comprovantes-list',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './comprovantes-list.html',
  styleUrls: ['../cartoes/cartoes.css', '../faturas/faturas.css', '../faturas/importacao.css', './comprovantes.css'],
})
export class ComprovantesList implements OnInit {
  private readonly service = inject(ComprovanteService);

  protected readonly comprovantes = signal<ComprovanteLista[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rotuloStatus = ROTULO_STATUS_COMPROVANTE;
  protected readonly rotuloModalidade = rotuloModalidade;

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listar().subscribe({
      next: (comprovantes) => {
        this.comprovantes.set(comprovantes);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErro(err, 'Não foi possível carregar os comprovantes.'));
        this.loading.set(false);
      },
    });
  }
}
