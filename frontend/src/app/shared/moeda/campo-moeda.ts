import { booleanAttribute, Directive, effect, ElementRef, forwardRef, inject, input, output, untracked } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { formatarDigitacaoMoeda, formatarValorMoeda, paraNumeroMoeda } from './moeda';

/**
 * Campo de valor em dinheiro: formata enquanto o usuário digita ("1250,52" vira "1.250,52") e
 * entrega um número ao form (ou null quando vazio). Funciona com formControlName/formControl ou,
 * fora de forms, com [valor] e (valorChange). Com permitirNegativo, aceita o sinal "-" (reembolso).
 */
@Directive({
  selector: 'input[appCampoMoeda]',
  host: {
    type: 'text',
    inputmode: 'decimal',
    autocomplete: 'off',
    '(input)': 'aoDigitar($event)',
    '(blur)': 'aoSair()',
  },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CampoMoeda), multi: true }],
})
export class CampoMoeda implements ControlValueAccessor {
  private readonly campo = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  /** Valor exibido quando o campo não está ligado a um form. */
  readonly valor = input<number | null | undefined>(undefined);
  readonly valorChange = output<number | null>();
  /** Aceita valores negativos, como reembolsos em fatura. */
  readonly permitirNegativo = input(false, { transform: booleanAttribute });

  private onChange: (valor: number | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    // Com o foco no campo, o valor que volta do pai é o que o usuário acabou de digitar
    // (ou um padrão, como 0 para vazio); reescrever aqui atrapalharia a digitação.
    effect(() => {
      const valor = this.valor();
      if (valor !== undefined && document.activeElement !== this.campo) {
        untracked(() => this.exibir(valor));
      }
    });
  }

  writeValue(valor: number | null | undefined): void {
    this.exibir(valor ?? null);
  }

  registerOnChange(fn: (valor: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(desabilitado: boolean): void {
    this.campo.disabled = desabilitado;
  }

  protected aoDigitar(evento: Event): void {
    let texto = this.campo.value;
    let cursor = this.campo.selectionStart ?? texto.length;

    // Teclados de celular que só mostram ponto: o ponto digitado vira a vírgula decimal.
    const digitado = (evento as InputEvent).data;
    if (digitado === '.' && cursor > 0 && texto[cursor - 1] === '.' && !texto.includes(',')) {
      texto = `${texto.slice(0, cursor - 1)},${texto.slice(cursor)}`;
    }

    const significativosAntes = this.significativos(texto.slice(0, cursor));
    const formatado = formatarDigitacaoMoeda(texto, this.permitirNegativo());
    this.campo.value = formatado;

    cursor = this.posicaoApos(formatado, significativosAntes);
    if (document.activeElement === this.campo) {
      this.campo.setSelectionRange(cursor, cursor);
    }

    const valor = paraNumeroMoeda(formatado);
    this.onChange(valor);
    this.valorChange.emit(valor);
  }

  protected aoSair(): void {
    const valor = paraNumeroMoeda(this.campo.value);
    if (valor !== null) {
      this.campo.value = formatarValorMoeda(valor);
    }
    this.onTouched();
  }

  /** Só reescreve o texto quando o número muda, para não atrapalhar quem está digitando. */
  private exibir(valor: number | null): void {
    if (paraNumeroMoeda(this.campo.value) === valor) {
      return;
    }
    this.campo.value = valor === null ? '' : formatarValorMoeda(valor);
  }

  /** Quantos caracteres do texto sobrevivem à formatação (dígitos, vírgula e o sinal). */
  private significativos(texto: string): number {
    return texto.replace(this.permitirNegativo() ? /[^\d,-]/g : /[^\d,]/g, '').length;
  }

  /** Posição no texto formatado logo depois de `quantidade` caracteres significativos. */
  private posicaoApos(texto: string, quantidade: number): number {
    if (quantidade <= 0) {
      return 0;
    }
    let contados = 0;
    for (let i = 0; i < texto.length; i++) {
      if (this.significativos(texto[i]) === 1 && ++contados === quantidade) {
        return i + 1;
      }
    }
    return texto.length;
  }
}
