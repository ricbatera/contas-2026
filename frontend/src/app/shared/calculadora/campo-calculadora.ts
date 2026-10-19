import { Component, ElementRef, inject } from '@angular/core';
import { CalculadoraService } from './calculadora.service';

/**
 * Envolve um campo de valor e adiciona o botão da calculadora. O resultado é gravado no input
 * com um evento "input", que atualiza tanto formControlName quanto handlers (input).
 */
@Component({
  selector: 'app-campo-calculadora',
  template: `
    <ng-content />
    <button
      class="calculadora-botao"
      type="button"
      aria-label="Abrir calculadora"
      title="Calculadora"
      (click)="abrir($event)"
    >
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="5" y="2.5" width="14" height="19" rx="2" />
        <rect x="8" y="5.5" width="8" height="4" rx="0.5" />
        <path d="M8.5 13h.01M12 13h.01M15.5 13h.01M8.5 16.5h.01M12 16.5h.01M15.5 16.5h.01" />
      </svg>
    </button>
  `,
  styles: `
    :host {
      display: flex;
      align-items: stretch;
      gap: 8px;
    }

    :host > :not(.calculadora-botao) {
      min-width: 0;
      flex: 1 1 auto;
    }

    .calculadora-botao {
      display: grid;
      flex: 0 0 auto;
      min-width: 40px;
      place-items: center;
      padding: 0 10px;
      border: 1px solid #dce9f8;
      border-radius: 7px;
      color: #1e6fd0;
      background: #f4f9ff;
      cursor: pointer;
    }

    .calculadora-botao:hover {
      border-color: #1e6fd0;
    }
  `,
})
export class CampoCalculadora {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly calculadora = inject(CalculadoraService);

  protected async abrir(evento: Event): Promise<void> {
    evento.preventDefault();
    const campo = this.host.nativeElement.querySelector('input');
    if (!campo || campo.disabled || campo.readOnly) {
      return;
    }
    const valor = await this.calculadora.abrir(campo.value);
    if (valor === null) {
      return;
    }
    campo.value = String(valor);
    campo.dispatchEvent(new Event('input', { bubbles: true }));
    campo.focus();
  }
}
