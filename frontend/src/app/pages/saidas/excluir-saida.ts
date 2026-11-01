import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { Saida, SaidaService } from '../../core/saida.service';
import { DialogoRapido, mensagemErro } from '../../shared/cadastro-rapido/dialogo-rapido';

type ModoExclusao = 'TODAS' | 'FUTURAS';

/**
 * Exclusão de saída parcelada no cartão: todas as parcelas (lançamento errado) ou só as futuras
 * (serviço cancelado), a partir da parcela que o usuário escolher.
 */
@Component({
  selector: 'app-excluir-saida',
  imports: [CurrencyPipe, DatePipe, DialogoRapido],
  templateUrl: './excluir-saida.html',
  styleUrls: [
    '../cartoes/cartoes.css',
    './saidas.css',
    '../../shared/cadastro-rapido/cadastro-rapido.css',
    './excluir-saida.css',
  ],
})
export class ExcluirSaida implements OnInit {
  private readonly service = inject(SaidaService);

  readonly saida = input.required<Saida>();
  readonly excluida = output<string>();
  readonly atualizada = output<Saida>();
  readonly cancelado = output<void>();

  protected readonly modo = signal<ModoExclusao>('FUTURAS');
  protected readonly aPartirDe = signal(2);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly parcelas = computed(() =>
    [...this.saida().parcelas].sort((a, b) => a.numero - b.numero),
  );
  /** A 1ª parcela não entra: excluir a partir dela é excluir tudo. */
  protected readonly opcoesParcela = computed(() => this.parcelas().filter((parcela) => parcela.numero >= 2));
  protected readonly excluidas = computed(() =>
    this.parcelas().filter((parcela) => parcela.numero >= this.aPartirDe()),
  );
  protected readonly ficam = computed(() => this.parcelas().length - this.excluidas().length);
  protected readonly totalExcluido = computed(() =>
    this.excluidas().reduce((centavos, parcela) => centavos + Math.round(parcela.valor * 100), 0) / 100,
  );

  ngOnInit(): void {
    const hoje = new Date();
    const hojeFormatado = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
    const opcoes = this.opcoesParcela();
    const primeiraFutura = opcoes.find((parcela) => parcela.vencimento > hojeFormatado) ?? opcoes.at(-1);
    this.aPartirDe.set(primeiraFutura?.numero ?? 2);
  }

  protected escolherModo(modo: ModoExclusao): void {
    this.modo.set(modo);
    this.error.set(null);
  }

  protected escolherParcela(event: Event): void {
    this.aPartirDe.set(Number((event.target as HTMLSelectElement).value));
    this.error.set(null);
  }

  protected confirmar(): void {
    if (this.saving()) {
      return;
    }
    const saida = this.saida();
    this.saving.set(true);
    this.error.set(null);
    if (this.modo() === 'TODAS') {
      this.service.excluir(saida.id).subscribe({
        next: () => this.excluida.emit(saida.id),
        error: (err: HttpErrorResponse) => this.falhou(err, `Não foi possível excluir "${saida.descricao}".`),
      });
      return;
    }
    this.service.excluirParcelasAPartirDe(saida.id, this.aPartirDe()).subscribe({
      next: (atualizada) => this.atualizada.emit(atualizada),
      error: (err: HttpErrorResponse) => this.falhou(err, 'Não foi possível excluir as parcelas futuras.'),
    });
  }

  private falhou(err: HttpErrorResponse, fallback: string): void {
    this.error.set(mensagemErro(err, fallback));
    this.saving.set(false);
  }
}
