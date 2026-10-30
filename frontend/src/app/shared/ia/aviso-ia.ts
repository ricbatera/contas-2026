import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SituacaoIa } from '../../core/provedor-ia.service';

/**
 * Avisa que o recurso depende de IA e ainda não há provedor padrão ativo, com link para o cadastro.
 * Não aparece enquanto a situação é verificada nem quando a IA está disponível.
 */
@Component({
  selector: 'app-aviso-ia',
  imports: [RouterLink],
  template: `
    @if (situacao() === 'SEM_PROVEDOR') {
      <div class="aviso-ia" role="alert">
        <strong>Este recurso usa IA.</strong>
        <span>Você ainda não cadastrou nenhuma integração de IA.</span>
        <a routerLink="/home/ia/provedores/novo">Cadastrar integração de IA</a>
      </div>
    } @else if (situacao() === 'SEM_PADRAO_ATIVO') {
      <div class="aviso-ia" role="alert">
        <strong>Este recurso usa IA.</strong>
        <span>Nenhuma integração de IA está marcada como padrão e ativa.</span>
        <a routerLink="/home/ia/provedores">Configurar integrações de IA</a>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      grid-column: 1 / -1;
    }

    :host:empty {
      display: none;
    }

    .aviso-ia {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 8px;
      align-items: baseline;
      padding: 11px 13px;
      border: 1px solid #f0d9a8;
      border-radius: 7px;
      color: #8a5a00;
      background: #fff4dc;
      font-size: 12px;
      line-height: 1.5;
    }

    .aviso-ia a {
      color: #1761b5;
      font-weight: 600;
    }
  `,
})
export class AvisoIa {
  readonly situacao = input.required<SituacaoIa>();
}
