import { CurrencyPipe, DatePipe, registerLocaleData } from '@angular/common';
import { RouterLink } from '@angular/router';
import localePtBr from '@angular/common/locales/pt';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Cartao, CartaoService } from '../../core/cartao.service';
import { Fatura, FaturaCompetencia, FaturaParcela, FaturaService } from '../../core/fatura.service';

registerLocaleData(localePtBr);

@Component({
  selector: 'app-faturas',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './faturas.html',
  styleUrls: ['../cartoes/cartoes.css', './faturas.css', './importacao.css'],
})
export class Faturas implements OnInit {
  private readonly cartaoService = inject(CartaoService);
  private readonly faturaService = inject(FaturaService);

  protected readonly cartoes = signal<Cartao[]>([]);
  protected readonly competencias = signal<FaturaCompetencia[]>([]);
  protected readonly cartaoId = signal('');
  protected readonly competencia = signal<number | null>(null);
  protected readonly fatura = signal<Fatura | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.carregarCartoes();
  }

  protected carregarCartoes(): void {
    this.loading.set(true);
    this.error.set(null);
    this.cartaoService.listarPrincipais().subscribe({
      next: (cartoes) => {
        this.cartoes.set(cartoes);
        const cartaoId = this.cartaoId() && cartoes.some((cartao) => cartao.id === this.cartaoId())
          ? this.cartaoId()
          : cartoes[0]?.id ?? '';
        this.cartaoId.set(cartaoId);
        if (cartaoId) {
          this.carregarCompetencias(cartaoId);
        } else {
          this.competencias.set([]);
          this.competencia.set(null);
          this.fatura.set(null);
          this.loading.set(false);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar os cartões.'));
        this.loading.set(false);
      },
    });
  }

  protected selecionarCartao(event: Event): void {
    const cartaoId = (event.target as HTMLSelectElement).value;
    this.cartaoId.set(cartaoId);
    this.competencias.set([]);
    this.competencia.set(null);
    this.fatura.set(null);
    if (cartaoId) {
      this.carregarCompetencias(cartaoId);
    }
  }

  protected selecionarCompetencia(event: Event): void {
    const competencia = Number((event.target as HTMLSelectElement).value);
    if (Number.isInteger(competencia) && competencia > 0) {
      this.competencia.set(competencia);
      this.carregarFatura(this.cartaoId(), competencia);
    }
  }

  protected nomesResponsaveis(parcela: FaturaParcela): string {
    return parcela.responsaveis.map((responsavel) => responsavel.nome).join(', ');
  }

  protected rotuloCompetencia(competencia: number): string {
    const ano = Math.floor(competencia / 100);
    const mes = competencia % 100;
    return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })
      .format(new Date(ano, mes - 1, 1));
  }

  private carregarCompetencias(cartaoId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.faturaService.listarCompetencias(cartaoId).subscribe({
      next: (competencias) => {
        if (this.cartaoId() !== cartaoId) {
          return;
        }
        this.competencias.set(competencias);
        if (!competencias.length) {
          this.competencia.set(null);
          this.fatura.set(null);
          this.loading.set(false);
          return;
        }
        const atual = new Date();
        const competenciaAtual = atual.getFullYear() * 100 + atual.getMonth() + 1;
        const selecionada = competencias.find((item) => item.competencia === competenciaAtual)
          ?? competencias[0];
        this.competencia.set(selecionada.competencia);
        this.carregarFatura(cartaoId, selecionada.competencia);
      },
      error: (err: HttpErrorResponse) => {
        if (this.cartaoId() === cartaoId) {
          this.error.set(this.mensagemErro(err, 'Não foi possível carregar as competências.'));
          this.loading.set(false);
        }
      },
    });
  }

  private carregarFatura(cartaoId: string, competencia: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.faturaService.buscar(cartaoId, competencia).subscribe({
      next: (fatura) => {
        if (this.cartaoId() === cartaoId && this.competencia() === competencia) {
          this.fatura.set(fatura);
          this.loading.set(false);
        }
      },
      error: (err: HttpErrorResponse) => {
        if (this.cartaoId() === cartaoId && this.competencia() === competencia) {
          this.error.set(this.mensagemErro(err, 'Não foi possível carregar a fatura.'));
          this.loading.set(false);
        }
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
