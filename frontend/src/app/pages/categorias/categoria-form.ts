import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CategoriaRequest,
  CategoriaService,
  TIPOS_CATEGORIA,
} from '../../core/categoria.service';

@Component({
  selector: 'app-categoria-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './categoria-form.html',
  styleUrls: ['../cartoes/cartoes.css', './categorias.css'],
})
export class CategoriaForm {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CategoriaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly tipos = TIPOS_CATEGORIA;
  protected readonly categoriaId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.categoriaId;
  protected readonly loading = signal(this.editing);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    tipo: [this.route.snapshot.queryParamMap.get('tipo') === 'RECEITA' ? 'RECEITA' as const : 'DESPESA' as const],
    ativo: [true],
  });

  constructor() {
    if (this.categoriaId) {
      this.service.buscarPorId(this.categoriaId).subscribe({
        next: (categoria) => {
          this.form.setValue({ nome: categoria.nome, tipo: categoria.tipo, ativo: categoria.ativo });
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(this.mensagemErro(err, 'Não foi possível carregar a categoria.'));
          this.loading.set(false);
        },
      });
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.loading() || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: CategoriaRequest = { ...value, nome: value.nome.trim() };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.categoriaId
      ? this.service.atualizar(this.categoriaId, request)
      : this.service.criar(request);
    operation.subscribe({
      next: () => this.router.navigate(['/home/categorias']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar a categoria.'));
        this.saving.set(false);
      },
    });
  }

  private mensagemErro(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
    }
    return typeof err.error?.message === 'string'
      ? err.error.message
      : `${fallback} (erro ${err.status}).`;
  }
}
