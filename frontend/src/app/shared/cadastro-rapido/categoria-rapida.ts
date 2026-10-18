import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Categoria, CategoriaService, TipoCategoria } from '../../core/categoria.service';
import { DialogoRapido, mensagemErro } from './dialogo-rapido';

/**
 * Cria uma categoria (de despesa, por padrão) já com a primeira subcategoria, porque os formulários
 * de saída e de entrada só oferecem categorias que tenham subcategoria ativa.
 */
@Component({
  selector: 'app-categoria-rapida',
  imports: [DialogoRapido, ReactiveFormsModule],
  template: `
    <app-dialogo-rapido [titulo]="tipo() === 'RECEITA' ? 'Nova categoria de receita' : 'Nova categoria de despesa'" (fechar)="cancelado.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="filter-dialog-content">
          @if (error()) { <div class="form-error filter-full" role="alert">{{ error() }}</div> }
          <label class="form-field">
            <span>Categoria *</span>
            <input type="text" formControlName="nome" maxlength="100" autocomplete="off" placeholder="Ex.: Pets" />
            @if (form.controls.nome.invalid && form.controls.nome.touched) {
              <small>Informe um nome com até 100 caracteres.</small>
            }
          </label>
          <label class="form-field">
            <span>Primeira subcategoria *</span>
            <input type="text" formControlName="subcategoria" maxlength="100" autocomplete="off" placeholder="Ex.: Ração" />
            @if (form.controls.subcategoria.invalid && form.controls.subcategoria.touched) {
              <small>Informe um nome com até 100 caracteres.</small>
            }
          </label>
        </div>
        <footer class="filter-dialog-actions cadastro-rapido-actions">
          <button class="secondary-button" type="button" (click)="cancelado.emit()">Cancelar</button>
          <button class="primary-button" type="submit" [disabled]="saving()">
            {{ saving() ? 'Salvando...' : 'Cadastrar categoria' }}
          </button>
        </footer>
      </form>
    </app-dialogo-rapido>
  `,
  styleUrls: ['../../pages/cartoes/cartoes.css', '../../pages/saidas/saidas.css', './cadastro-rapido.css'],
})
export class CategoriaRapida {
  private readonly service = inject(CategoriaService);

  readonly tipo = input<TipoCategoria>('DESPESA');
  readonly salvo = output<Categoria>();
  readonly cancelado = output<void>();
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    subcategoria: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
  });
  /** Categoria já gravada quando a subcategoria falhou; o novo envio cria só a subcategoria. */
  private categoriaCriada: Categoria | null = null;

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    if (this.categoriaCriada) {
      this.criarSubcategoria(this.categoriaCriada);
      return;
    }
    this.service
      .criar({ nome: this.form.controls.nome.value.trim(), tipo: this.tipo(), ativo: true })
      .subscribe({
        next: (categoria) => {
          this.categoriaCriada = categoria;
          this.form.controls.nome.disable();
          this.criarSubcategoria(categoria);
        },
        error: (err: HttpErrorResponse) => this.falhar(err, 'Não foi possível cadastrar a categoria.'),
      });
  }

  private criarSubcategoria(categoria: Categoria): void {
    this.service
      .criarSubcategoria(categoria.id, {
        nome: this.form.controls.subcategoria.value.trim(),
        ativo: true,
        padrao: true,
      })
      .subscribe({
        next: (subcategoria) => this.salvo.emit({ ...categoria, subcategorias: [subcategoria] }),
        error: (err: HttpErrorResponse) =>
          this.falhar(err, `A categoria "${categoria.nome}" foi criada, mas não foi possível cadastrar a subcategoria.`),
      });
  }

  private falhar(err: HttpErrorResponse, fallback: string): void {
    this.error.set(mensagemErro(err, fallback));
    this.saving.set(false);
  }
}
