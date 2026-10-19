import { DOCUMENT } from '@angular/common';
import { ApplicationRef, createComponent, EnvironmentInjector, inject, Injectable } from '@angular/core';
import { Calculadora } from './calculadora';

/**
 * Abre a calculadora no body, fora da label do campo, para que cliques no diálogo
 * não acionem a label nem tirem o foco da expressão.
 */
@Injectable({ providedIn: 'root' })
export class CalculadoraService {
  private readonly appRef = inject(ApplicationRef);
  private readonly injector = inject(EnvironmentInjector);
  private readonly document = inject(DOCUMENT);

  /** Resolve com o valor escolhido, ou null se o usuário cancelar. */
  abrir(valorInicial: string): Promise<number | null> {
    const ref = createComponent(Calculadora, { environmentInjector: this.injector });
    ref.setInput('valorInicial', valorInicial);
    this.appRef.attachView(ref.hostView);
    this.document.body.appendChild(ref.location.nativeElement);

    return new Promise((resolve) => {
      const fechar = (valor: number | null) => {
        this.appRef.detachView(ref.hostView);
        ref.destroy();
        resolve(valor);
      };
      ref.instance.usar.subscribe((valor) => fechar(valor));
      ref.instance.cancelado.subscribe(() => fechar(null));
    });
  }
}
