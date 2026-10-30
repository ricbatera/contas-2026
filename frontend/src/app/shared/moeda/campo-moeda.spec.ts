import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CampoMoeda } from './campo-moeda';

@Component({
  imports: [CampoMoeda, ReactiveFormsModule],
  template: `
    <input id="form" appCampoMoeda [formControl]="controle" />
    <input id="livre" appCampoMoeda [valor]="livre()" (valorChange)="livre.set($event)" />
    <input id="reembolso" appCampoMoeda permitirNegativo [formControl]="reembolso" />
  `,
})
class HostTeste {
  readonly controle = new FormControl<number | null>(null);
  readonly reembolso = new FormControl<number | null>(null);
  readonly livre = signal<number | null>(null);
}

describe('CampoMoeda', () => {
  let fixture: ComponentFixture<HostTeste>;

  beforeEach(() => {
    fixture = TestBed.createComponent(HostTeste);
    fixture.detectChanges();
  });

  function campo(id: string): HTMLInputElement {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`#${id}`)!;
  }

  function digitar(input: HTMLInputElement, texto: string, data: string | null = null): void {
    input.value = texto;
    input.dispatchEvent(new InputEvent('input', { data }));
    fixture.detectChanges();
  }

  it('starts empty, as a decimal text field', () => {
    const input = campo('form');
    expect(input.value).toBe('');
    expect(input.type).toBe('text');
    expect(input.getAttribute('inputmode')).toBe('decimal');
  });

  it('formats while typing and gives the form a number', () => {
    const input = campo('form');
    digitar(input, '1250,52');
    expect(input.value).toBe('1.250,52');
    expect(fixture.componentInstance.controle.value).toBe(1250.52);

    digitar(input, '');
    expect(fixture.componentInstance.controle.value).toBeNull();
  });

  it('turns a typed dot into the decimal comma', () => {
    const input = campo('form');
    digitar(input, '12.', '.');
    expect(input.value).toBe('12,');
    digitar(input, '12,5');
    expect(fixture.componentInstance.controle.value).toBe(12.5);
  });

  it('accepts negatives only with permitirNegativo', () => {
    digitar(campo('form'), '-50');
    expect(fixture.componentInstance.controle.value).toBe(50);

    const reembolso = campo('reembolso');
    digitar(reembolso, '-1250,5');
    expect(reembolso.value).toBe('-1.250,5');
    expect(fixture.componentInstance.reembolso.value).toBe(-1250.5);

    digitar(reembolso, '-');
    expect(fixture.componentInstance.reembolso.value).toBeNull();

    fixture.componentInstance.reembolso.setValue(-30);
    expect(reembolso.value).toBe('-30,00');
  });

  it('completes the decimals on blur', () => {
    const input = campo('form');
    digitar(input, '1250,5');
    input.dispatchEvent(new Event('blur'));
    expect(input.value).toBe('1.250,50');
    expect(fixture.componentInstance.controle.touched).toBe(true);
  });

  it('shows values written by the form', () => {
    fixture.componentInstance.controle.setValue(1250.5);
    expect(campo('form').value).toBe('1.250,50');
    fixture.componentInstance.controle.setValue(null);
    expect(campo('form').value).toBe('');
  });

  it('works with [valor] and (valorChange) outside forms', () => {
    fixture.componentInstance.livre.set(99.9);
    fixture.detectChanges();
    const input = campo('livre');
    expect(input.value).toBe('99,90');

    digitar(input, '2000');
    expect(input.value).toBe('2.000');
    expect(fixture.componentInstance.livre()).toBe(2000);
  });
});
