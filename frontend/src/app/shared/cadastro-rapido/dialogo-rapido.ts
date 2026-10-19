import { HttpErrorResponse } from '@angular/common/http';
import { afterNextRender, Component, ElementRef, inject, input, output } from '@angular/core';

/** Moldura dos diálogos de cadastro rápido: fundo, cabeçalho e foco no primeiro campo. */
@Component({
  selector: 'app-dialogo-rapido',
  template: `
    <div class="filter-backdrop" (click)="fechar.emit()">
      <section
        class="filter-dialog cadastro-rapido-dialog"
        role="dialog"
        aria-modal="true"
        [attr.aria-labelledby]="tituloId"
        (click)="$event.stopPropagation()"
        (keydown.escape)="fechar.emit()"
      >
        <header class="filter-dialog-header">
          <div>
            <p class="section-kicker">{{ secao() }}</p>
            <h2 [id]="tituloId">{{ titulo() }}</h2>
          </div>
          <button class="filter-close-button" type="button" aria-label="Fechar" (click)="fechar.emit()">×</button>
        </header>
        <ng-content />
      </section>
    </div>
  `,
  styleUrls: ['../../pages/cartoes/cartoes.css', '../../pages/saidas/saidas.css', './cadastro-rapido.css'],
})
export class DialogoRapido {
  private static sequencia = 0;

  readonly titulo = input.required<string>();
  readonly secao = input('CADASTRO RÁPIDO');
  readonly fechar = output<void>();
  protected readonly tituloId = `cadastro-rapido-titulo-${++DialogoRapido.sequencia}`;

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    afterNextRender(() => host.nativeElement.querySelector<HTMLElement>('input, select')?.focus());
  }
}

export function mensagemErro(err: HttpErrorResponse, fallback: string): string {
  if (err.status === 0) {
    return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
  }
  return typeof err.error?.message === 'string'
    ? err.error.message
    : `${fallback} (erro ${err.status}).`;
}
