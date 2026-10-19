import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CampoCalculadora } from './campo-calculadora';

@Component({
  imports: [CampoCalculadora, ReactiveFormsModule],
  template: `
    <label>
      Valor
      <app-campo-calculadora>
        <input type="number" [formControl]="valor" />
      </app-campo-calculadora>
    </label>
  `,
})
class HostTeste {
  readonly valor = new FormControl<number | null>(12.5);
}

describe('CampoCalculadora', () => {
  let fixture: ComponentFixture<HostTeste>;

  beforeEach(() => {
    fixture = TestBed.createComponent(HostTeste);
    fixture.detectChanges();
  });

  afterEach(() => document.querySelectorAll('app-calculadora').forEach((el) => el.remove()));

  function abrir(): void {
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('button[aria-label="Abrir calculadora"]')!
      .click();
    fixture.detectChanges();
  }

  function dialogo(): HTMLElement | null {
    return document.querySelector('app-calculadora');
  }

  function botao(texto: string): HTMLButtonElement {
    return Array.from(dialogo()!.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
    )!;
  }

  function clicar(texto: string): void {
    botao(texto).click();
    fixture.detectChanges();
  }

  it('opens with the current value and writes the result back to the control', async () => {
    abrir();
    const expressao = dialogo()!.querySelector<HTMLInputElement>('input')!;
    expect(expressao.value).toBe('12,5');

    expressao.value = '10+5,5';
    expressao.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(dialogo()!.textContent).toContain('R$');

    clicar('Usar valor');
    await fixture.whenStable();

    expect(fixture.componentInstance.valor.value).toBe(15.5);
    expect(dialogo()).toBeNull();
  });

  it('builds the expression with the keypad', async () => {
    abrir();
    for (const tecla of ['Limpar', '3', '5', '0', 'Dividir', '3']) {
      clicar(tecla);
    }
    expect(botao('Usar valor').disabled).toBe(false);
    clicar('Calcular');
    expect(dialogo()!.querySelector<HTMLInputElement>('input')!.value).toBe('116,67');

    clicar('Usar valor');
    await fixture.whenStable();
    expect(fixture.componentInstance.valor.value).toBe(116.67);
  });

  it('keeps the value when cancelled and disables use for invalid expressions', async () => {
    abrir();
    clicar('Limpar');
    clicar('Somar');
    expect(botao('Usar valor').disabled).toBe(true);
    expect(dialogo()!.textContent).toContain('Expressão inválida');

    clicar('Cancelar');
    await fixture.whenStable();
    expect(fixture.componentInstance.valor.value).toBe(12.5);
    expect(dialogo()).toBeNull();
  });

  it('does not open for a disabled field', () => {
    fixture.componentInstance.valor.disable();
    fixture.detectChanges();
    abrir();
    expect(dialogo()).toBeNull();
  });
});
