import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PessoaRequest, PessoaService } from '../../core/pessoa.service';

@Component({
  selector: 'app-pessoa-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './pessoa-form.html',
  styleUrls: ['../cartoes/cartoes.css', './pessoas.css'],
})
export class PessoaForm {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PessoaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly pessoaId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.pessoaId;
  protected readonly loading = signal(this.editing);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    ativo: [true],
  });

  constructor() {
    if (this.pessoaId) {
      this.service.buscarPorId(this.pessoaId).subscribe({
        next: (pessoa) => {
          this.form.setValue({ nome: pessoa.nome, ativo: pessoa.ativo });
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(this.mensagemErro(err, 'Não foi possível carregar os dados da pessoa.'));
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
    const request: PessoaRequest = { nome: value.nome.trim(), ativo: value.ativo };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.pessoaId
      ? this.service.atualizar(this.pessoaId, request)
      : this.service.criar(request);

    operation.subscribe({
      next: () => this.router.navigate(['/home/pessoas']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar a pessoa.'));
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
