import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ComprovanteLista, ComprovanteService } from '../../core/comprovante.service';
import { mensagemErro } from '../faturas/importacao-util';
import { ROTULO_STATUS_COMPROVANTE, rotuloModalidade } from './comprovantes-util';
import { correspondeBusca } from '../../shared/busca/busca';
import { CampoBusca } from '../../shared/busca/campo-busca';

@Component({
  selector: 'app-comprovantes-list',
  imports: [CampoBusca, CurrencyPipe, DatePipe, RouterLink],
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
  protected readonly busca = signal('');
  protected readonly comprovantesEncontrados = computed(() =>
    this.comprovantes().filter((item) => correspondeBusca(
      this.busca(),
      item.descricao,
      item.estabelecimento,
      this.rotuloStatus[item.status],
      rotuloModalidade(item.modalidade, item.quantidadeParcelas),
      item.cartaoNome,
      item.cartaoUltimosDigitos,
      item.valorTotal,
    )));

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
