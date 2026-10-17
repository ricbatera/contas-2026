import { CurrencyPipe, DecimalPipe, registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Bucket, BucketService } from '../../core/bucket.service';

const MESES = Array.from({ length: 12 }, (_, index) => ({
  valor: index + 1,
  nome: new Intl.DateTimeFormat('pt-BR', { month: 'long', timeZone: 'UTC' })
    .format(new Date(Date.UTC(2020, index, 1))),
}));

registerLocaleData(localePtBr);

@Component({
  selector: 'app-buckets-list',
  imports: [CurrencyPipe, DecimalPipe, RouterLink],
  templateUrl: './buckets-list.html',
  styleUrls: ['../cartoes/cartoes.css', './buckets.css'],
})
export class BucketsList implements OnInit {
  private readonly service = inject(BucketService);
  protected readonly meses = MESES;
  protected readonly mes = signal(new Date().getMonth() + 1);
  protected readonly ano = signal(new Date().getFullYear());
  protected readonly buckets = signal<Bucket[]>([]);
  protected readonly loading = signal(true);
  protected readonly workingId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly competenciaError = signal<string | null>(null);

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    if (!Number.isInteger(this.mes()) || this.mes() < 1 || this.mes() > 12
      || !Number.isInteger(this.ano()) || this.ano() < 1900 || this.ano() > 9999) {
      this.competenciaError.set('Informe um mês e ano válidos para consultar os buckets.');
      return;
    }
    this.competenciaError.set(null);
    this.loading.set(true);
    this.error.set(null);
    this.service.listar(this.mes(), this.ano()).subscribe({
      next: (buckets) => {
        this.buckets.set(buckets);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar os buckets.'));
        this.loading.set(false);
      },
    });
  }

  protected atualizarMes(event: Event): void {
    this.mes.set(Number((event.target as HTMLSelectElement).value));
  }

  protected atualizarAno(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.ano.set(valor === '' ? Number.NaN : Number(valor));
  }

  protected excluir(bucket: Bucket): void {
    if (!window.confirm(`Deseja excluir o bucket de "${bucket.subcategoriaNome}" para ${bucket.responsavelNome}?`)) {
      return;
    }
    this.workingId.set(bucket.id);
    this.error.set(null);
    this.service.excluir(bucket.id).subscribe({
      next: () => {
        this.buckets.update((items) => items.filter((item) => item.id !== bucket.id));
        this.workingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível excluir o bucket.'));
        this.workingId.set(null);
      },
    });
  }

  protected percentualBarra(bucket: Bucket): number {
    return Math.min(Math.max(bucket.percentualConsumido, 0), 100);
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
