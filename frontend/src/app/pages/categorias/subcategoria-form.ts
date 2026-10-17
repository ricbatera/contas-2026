import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Categoria, CategoriaService, SubcategoriaRequest } from '../../core/categoria.service';

@Component({
  selector: 'app-subcategoria-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './subcategoria-form.html',
  styleUrls: ['../cartoes/cartoes.css', './categorias.css'],
})
export class SubcategoriaForm {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CategoriaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly subcategoriaId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.subcategoriaId;
  protected readonly categoriaId = signal(this.route.snapshot.paramMap.get('categoriaId'));
  protected readonly categoria = signal<Categoria | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    ativo: [true],
    padrao: [false],
  });

  constructor() {
    if (this.subcategoriaId) {
      this.service.buscarSubcategoria(this.subcategoriaId).subscribe({
        next: (subcategoria) => {
          this.categoriaId.set(subcategoria.categoriaId);
          this.form.setValue({
            nome: subcategoria.nome,
            ativo: subcategoria.ativo,
            padrao: subcategoria.padrao,
          });
          this.atualizarDisponibilidadePadrao();
          this.carregarCategoria(subcategoria.categoriaId);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(this.mensagemErro(err, 'Não foi possível carregar a subcategoria.'));
          this.loading.set(false);
        },
      });
    } else if (this.categoriaId()) {
      this.carregarCategoria(this.categoriaId()!);
    } else {
      this.error.set('Categoria não informada.');
      this.loading.set(false);
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.loading() || this.saving() || !this.categoriaId()) {
      this.form.markAllAsTouched();
      return;
    }
    const request: SubcategoriaRequest = {
      nome: this.form.controls.nome.value.trim(),
      ativo: this.form.controls.ativo.value,
      padrao: this.form.controls.padrao.value,
      categoriaId: this.categoriaId()!,
    };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.subcategoriaId
      ? this.service.atualizarSubcategoria(this.subcategoriaId, request)
      : this.service.criarSubcategoria(this.categoriaId()!, {
          nome: request.nome,
          ativo: request.ativo,
          padrao: request.padrao,
        });
    operation.subscribe({
      next: () => this.router.navigate(['/home/categorias']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar a subcategoria.'));
        this.saving.set(false);
      },
    });
  }

  protected aoAlterarStatus(): void {
    if (!this.form.controls.ativo.value) {
      this.form.controls.padrao.setValue(false);
    }
    this.atualizarDisponibilidadePadrao();
  }

  private atualizarDisponibilidadePadrao(): void {
    const habilitado = this.form.controls.ativo.value && this.categoria()?.ativo;
    if (habilitado) {
      this.form.controls.padrao.enable({ emitEvent: false });
    } else {
      this.form.controls.padrao.setValue(false, { emitEvent: false });
      this.form.controls.padrao.disable({ emitEvent: false });
    }
  }

  private carregarCategoria(id: string): void {
    this.service.buscarPorId(id).subscribe({
      next: (categoria) => {
        this.categoria.set(categoria);
        this.atualizarDisponibilidadePadrao();
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar a categoria.'));
        this.loading.set(false);
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
