import { afterNextRender, Component, DestroyRef, ElementRef, inject, Injector, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormGroup, FormGroupDirective } from '@angular/forms';

/**
 * Colocado dentro de um <form [formGroup]>: ao tentar salvar com erro, avisa o usuário, marca os
 * campos e rola a tela até o primeiro inválido (importante no celular, onde ele fica fora da tela).
 */
@Component({
  selector: 'app-aviso-formulario',
  template: `
    @if (mensagem(); as texto) {
      <div class="form-error aviso-formulario" role="alert">{{ texto }}</div>
    }
  `,
  styles: `
    /* Em formulários em grid, ocupa a linha inteira; sem aviso, não ocupa espaço nem gap. */
    :host {
      display: block;
      grid-column: 1 / -1;
    }

    :host:empty {
      display: none;
    }
  `,
})
export class AvisoFormulario implements OnInit {
  private readonly diretiva = inject(FormGroupDirective);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly mensagem = signal<string | null>(null);
  private enviado = false;

  ngOnInit(): void {
    this.diretiva.ngSubmit.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.aoEnviar());
    this.diretiva.form.statusChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.enviado && this.atualizarMensagem());
  }

  private aoEnviar(): void {
    const form = this.diretiva.form;
    if (form.valid || form.disabled) {
      this.enviado = false;
      this.mensagem.set(null);
      return;
    }
    this.enviado = true;
    form.markAllAsTouched();
    this.atualizarMensagem();
    afterNextRender(() => this.rolarAtePrimeiroInvalido(), { injector: this.injector });
  }

  private atualizarMensagem(): void {
    const form = this.diretiva.form;
    if (!form.invalid) {
      this.mensagem.set(null);
      return;
    }
    this.mensagem.set(temErroObrigatorio(form)
      ? 'Preencha os campos obrigatórios destacados.'
      : 'Corrija os campos destacados.');
  }

  private rolarAtePrimeiroInvalido(): void {
    const formulario = this.host.nativeElement.closest('form');
    const campo = formulario?.querySelector<HTMLElement>(
      'input.ng-invalid, select.ng-invalid, textarea.ng-invalid',
    );
    if (!campo) {
      return;
    }
    // "center" deixa o campo longe da barra superior fixa.
    if (typeof campo.scrollIntoView === 'function') {
      campo.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
    campo.focus({ preventScroll: true });
  }
}

function temErroObrigatorio(controle: AbstractControl): boolean {
  if (controle.disabled) {
    return false;
  }
  if (controle.hasError('required')) {
    return true;
  }
  if (controle instanceof FormGroup || controle instanceof FormArray) {
    return Object.values(controle.controls).some(temErroObrigatorio);
  }
  return false;
}
