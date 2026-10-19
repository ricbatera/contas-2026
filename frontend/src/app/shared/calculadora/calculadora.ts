import { CurrencyPipe, registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { Component, computed, input, OnInit, output, signal } from '@angular/core';
import { DialogoRapido } from '../cadastro-rapido/dialogo-rapido';
import { avaliarExpressao } from './expressao';

registerLocaleData(localePtBr);

/** Diálogo da calculadora dos campos de valor; "Usar valor" devolve o resultado com 2 casas. */
@Component({
  selector: 'app-calculadora',
  imports: [CurrencyPipe, DialogoRapido],
  template: `
    <app-dialogo-rapido titulo="Calcular valor" secao="CALCULADORA" (fechar)="cancelado.emit()">
      <div class="filter-dialog-content">
        <label class="form-field filter-full">
          <span>Expressão</span>
          <input
            type="text"
            inputmode="decimal"
            autocomplete="off"
            placeholder="Ex.: 120,50 + 35 * 2"
            [value]="expressao()"
            (input)="expressao.set($any($event.target).value)"
            (keydown.enter)="$event.preventDefault(); usarValor()"
          />
        </label>
        <p class="calculadora-resultado filter-full" aria-live="polite">
          @if (resultado() !== null) {
            = <strong>{{ resultado() | currency: 'BRL' : 'symbol' : '1.2-2' : 'pt-BR' }}</strong>
          } @else if (expressao().trim()) {
            Expressão inválida
          }
        </p>
        <div class="calculadora-teclado filter-full">
          @for (tecla of teclas; track tecla) {
            <button
              type="button"
              class="calculadora-tecla"
              [class.calculadora-operador]="operadores.includes(tecla)"
              [attr.aria-label]="rotulos[tecla] ?? tecla"
              (click)="pressionar(tecla)"
            >{{ tecla }}</button>
          }
        </div>
      </div>
      <footer class="filter-dialog-actions cadastro-rapido-actions">
        <button class="secondary-button" type="button" (click)="cancelado.emit()">Cancelar</button>
        <button class="primary-button" type="button" [disabled]="resultado() === null" (click)="usarValor()">
          Usar valor
        </button>
      </footer>
    </app-dialogo-rapido>
  `,
  styleUrls: [
    '../../pages/cartoes/cartoes.css',
    '../../pages/saidas/saidas.css',
    '../cadastro-rapido/cadastro-rapido.css',
    './calculadora.css',
  ],
})
export class Calculadora implements OnInit {
  readonly valorInicial = input('');
  readonly usar = output<number>();
  readonly cancelado = output<void>();

  protected readonly teclas = ['C', '⌫', '(', ')', '7', '8', '9', '÷', '4', '5', '6', '×', '1', '2', '3', '−', '0', ',', '=', '+'];
  protected readonly operadores = ['÷', '×', '−', '+', '='];
  protected readonly rotulos: Partial<Record<string, string>> = {
    C: 'Limpar',
    '⌫': 'Apagar',
    '÷': 'Dividir',
    '×': 'Multiplicar',
    '−': 'Subtrair',
    '+': 'Somar',
    '=': 'Calcular',
  };
  protected readonly expressao = signal('');
  protected readonly resultado = computed(() => avaliarExpressao(this.expressao()));

  ngOnInit(): void {
    this.expressao.set(this.valorInicial().replace('.', ','));
  }

  protected pressionar(tecla: string): void {
    if (tecla === 'C') {
      this.expressao.set('');
    } else if (tecla === '⌫') {
      this.expressao.update((texto) => texto.trimEnd().slice(0, -1));
    } else if (tecla === '=') {
      const resultado = this.resultado();
      if (resultado !== null) {
        this.expressao.set(String(resultado).replace('.', ','));
      }
    } else {
      this.expressao.update((texto) => texto + tecla);
    }
  }

  protected usarValor(): void {
    const resultado = this.resultado();
    if (resultado !== null) {
      this.usar.emit(resultado);
    }
  }
}
