import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Pessoa, PessoaService } from '../../core/pessoa.service';
import { DialogoRapido, mensagemErro } from './dialogo-rapido';

@Component({
  selector: 'app-pessoa-rapida',
  imports: [DialogoRapido, ReactiveFormsModule],
  template: `
    <app-dialogo-rapido titulo="Nova pessoa" (fechar)="cancelado.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="filter-dialog-content">
          @if (error()) { <div class="form-error filter-full" role="alert">{{ error() }}</div> }
          <label class="form-field filter-full">
            <span>Nome *</span>
            <input type="text" formControlName="nome" maxlength="100" autocomplete="off" placeholder="Ex.: Maria" />
            @if (form.controls.nome.invalid && form.controls.nome.touched) {
              <small>Informe um nome com até 100 caracteres.</small>
            }
          </label>
        </div>
        <footer class="filter-dialog-actions cadastro-rapido-actions">
          <button class="secondary-button" type="button" (click)="cancelado.emit()">Cancelar</button>
          <button class="primary-button" type="submit" [disabled]="saving()">
            {{ saving() ? 'Salvando...' : 'Cadastrar pessoa' }}
          </button>
        </footer>
      </form>
    </app-dialogo-rapido>
  `,
  styleUrls: ['../../pages/cartoes/cartoes.css', '../../pages/saidas/saidas.css', './cadastro-rapido.css'],
})
export class PessoaRapida {
  private readonly service = inject(PessoaService);

  readonly salvo = output<Pessoa>();
  readonly cancelado = output<void>();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.service.criar({ nome: this.form.controls.nome.value.trim(), ativo: true }).subscribe({
      next: (pessoa) => this.salvo.emit(pessoa),
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErro(err, 'Não foi possível cadastrar a pessoa.'));
        this.saving.set(false);
      },
    });
  }
}
