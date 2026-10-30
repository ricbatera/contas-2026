import { Component, input, model } from '@angular/core';

/**
 * Campo de busca das listagens (telas e modais): filtra enquanto o usuário digita. A tela guarda o
 * termo num signal (`[(termo)]`) e filtra a lista com `correspondeBusca`.
 */
@Component({
  selector: 'app-campo-busca',
  template: `
    <div class="campo-busca">
      <svg class="campo-busca-icone" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M10 3a7 7 0 0 1 5.6 11.2l5.1 5.1-1.4 1.4-5.1-5.1A7 7 0 1 1 10 3zm0 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10z" />
      </svg>
      <input
        type="search"
        [value]="termo()"
        [placeholder]="placeholder()"
        [attr.aria-label]="placeholder()"
        autocomplete="off"
        (input)="termo.set($any($event.target).value)"
        (keydown.escape)="limpar($event)"
      />
      @if (termo()) {
        <button class="campo-busca-limpar" type="button" aria-label="Limpar busca" (click)="termo.set('')">×</button>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      max-width: 360px;
      margin-bottom: 16px;
    }

    :host(.campo-busca-largo) {
      max-width: none;
    }

    .campo-busca {
      position: relative;
      display: flex;
      align-items: center;
    }

    .campo-busca-icone {
      position: absolute;
      left: 11px;
      width: 16px;
      height: 16px;
      fill: #7a8699;
      pointer-events: none;
    }

    input {
      width: 100%;
      min-height: 38px;
      padding: 8px 34px;
      border: 1px solid #d6dde8;
      border-radius: 7px;
      background: #fff;
      color: inherit;
      font: inherit;
      font-size: 13px;
    }

    input:focus {
      outline: 2px solid #1761b5;
      outline-offset: -1px;
    }

    /* O "×" do navegador duplicaria o botão de limpar. */
    input::-webkit-search-cancel-button {
      display: none;
    }

    .campo-busca-limpar {
      position: absolute;
      right: 6px;
      width: 26px;
      height: 26px;
      border: 0;
      border-radius: 50%;
      background: transparent;
      color: #5b6577;
      font-size: 18px;
      line-height: 1;
      cursor: pointer;
    }

    .campo-busca-limpar:hover {
      background: #eef2f7;
    }
  `,
})
export class CampoBusca {
  readonly termo = model('');
  readonly placeholder = input('Buscar');

  protected limpar(event: Event): void {
    if (this.termo()) {
      // Num modal, o primeiro Escape só limpa a busca; o segundo fecha.
      event.stopPropagation();
      this.termo.set('');
    }
  }
}
