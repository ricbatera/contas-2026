import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import {
  ContaFixaPrevia,
  GerarContasFixasResponse,
  SaidaService,
} from '../../core/saida.service';
import { CampoCalculadora } from '../../shared/calculadora/campo-calculadora';
import { CampoMoeda } from '../../shared/moeda/campo-moeda';
import { DialogoRapido, mensagemErro } from '../../shared/cadastro-rapido/dialogo-rapido';

interface LinhaContaFixa {
  item: ContaFixaPrevia;
  selecionada: boolean;
  valor: number | null;
  data: string;
}

/** Revisão das contas fixas propostas para o mês: nada é gravado até o usuário aprovar. */
@Component({
  selector: 'app-gerar-contas-fixas',
  imports: [CampoCalculadora, CampoMoeda, CurrencyPipe, DialogoRapido],
  templateUrl: './gerar-contas-fixas.html',
  styleUrls: [
    '../cartoes/cartoes.css',
    './saidas.css',
    '../../shared/cadastro-rapido/cadastro-rapido.css',
    './gerar-contas-fixas.css',
  ],
})
export class GerarContasFixas implements OnInit {
  private readonly service = inject(SaidaService);

  readonly itens = input.required<ContaFixaPrevia[]>();
  readonly mes = input.required<number>();
  readonly ano = input.required<number>();
  readonly gerado = output<GerarContasFixasResponse>();
  readonly cancelado = output<void>();

  protected readonly linhas = signal<LinhaContaFixa[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selecionadas = computed(() => this.linhas().filter((linha) => linha.selecionada));
  protected readonly total = computed(() =>
    this.selecionadas().reduce((centavos, linha) => centavos + Math.round(Number(linha.valor) * 100), 0) / 100,
  );
  protected readonly titulo = computed(() => {
    const nomeMes = new Intl.DateTimeFormat('pt-BR', { month: 'long', timeZone: 'UTC' })
      .format(new Date(Date.UTC(this.ano(), this.mes() - 1, 1)));
    return `Contas fixas de ${nomeMes} de ${this.ano()}`;
  });
  protected readonly primeiroDia = computed(() => this.dataIso(this.ano(), this.mes(), 1));
  protected readonly ultimoDia = computed(() =>
    this.dataIso(this.ano(), this.mes(), new Date(Date.UTC(this.ano(), this.mes(), 0)).getUTCDate()),
  );

  ngOnInit(): void {
    this.linhas.set(this.itens().map((item) => ({
      item,
      selecionada: !item.aviso,
      valor: item.valorPorParcela,
      data: item.dataSugerida,
    })));
  }

  protected alternar(serieId: string, event: Event): void {
    const selecionada = (event.target as HTMLInputElement).checked;
    this.atualizar(serieId, { selecionada });
  }

  protected alterarValor(serieId: string, valor: number | null): void {
    this.atualizar(serieId, { valor });
  }

  protected alterarData(serieId: string, event: Event): void {
    this.atualizar(serieId, { data: (event.target as HTMLInputElement).value });
  }

  protected aprovar(): void {
    const selecionadas = this.selecionadas();
    if (selecionadas.length === 0 || this.saving()) {
      return;
    }
    const invalida = selecionadas.find((linha) => linha.valor === null || linha.valor < 0.01
      || !linha.data || linha.data < this.primeiroDia() || linha.data > this.ultimoDia());
    if (invalida) {
      this.error.set(`Revise "${invalida.item.descricao}": informe um valor positivo e uma data dentro do mês.`);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.service.gerarContasFixas({
      mes: this.mes(),
      ano: this.ano(),
      itens: selecionadas.map((linha) => ({
        serieId: linha.item.serieId,
        valorPorParcela: Math.round(Number(linha.valor) * 100) / 100,
        data: linha.data,
      })),
    }).subscribe({
      next: (resposta) => this.gerado.emit(resposta),
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErro(err, 'Não foi possível gerar as contas fixas.'));
        this.saving.set(false);
      },
    });
  }

  protected nomeForma(forma: ContaFixaPrevia['formaPagamento']): string {
    return { CARTAO_CREDITO: 'Cartão de crédito', BOLETO: 'Boleto', DEBITO: 'Débito', PIX: 'Pix' }[forma];
  }

  private atualizar(serieId: string, alteracao: Partial<LinhaContaFixa>): void {
    this.linhas.update((linhas) =>
      linhas.map((linha) => (linha.item.serieId === serieId ? { ...linha, ...alteracao } : linha)),
    );
    this.error.set(null);
  }

  private dataIso(ano: number, mes: number, dia: number): string {
    return `${ano}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
  }
}
