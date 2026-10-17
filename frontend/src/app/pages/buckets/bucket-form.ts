import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BucketRequest, BucketService } from '../../core/bucket.service';
import { Categoria, CategoriaService, Subcategoria } from '../../core/categoria.service';
import { Pessoa, PessoaService } from '../../core/pessoa.service';
import { CategoriaRapida } from '../../shared/cadastro-rapido/categoria-rapida';
import { PessoaRapida } from '../../shared/cadastro-rapido/pessoa-rapida';
import { SubcategoriaRapida } from '../../shared/cadastro-rapido/subcategoria-rapida';

type CadastroRapido =
  | { tipo: 'categoria' }
  | { tipo: 'subcategoria'; categoria: Categoria }
  | { tipo: 'pessoa' };

@Component({
  selector: 'app-bucket-form',
  imports: [CategoriaRapida, PessoaRapida, ReactiveFormsModule, RouterLink, SubcategoriaRapida],
  templateUrl: './bucket-form.html',
  styleUrls: ['../cartoes/cartoes.css', './buckets.css', '../../shared/cadastro-rapido/cadastro-rapido.css'],
})
export class BucketForm {
  private readonly fb = inject(FormBuilder);
  private readonly bucketService = inject(BucketService);
  private readonly categoriaService = inject(CategoriaService);
  private readonly pessoaService = inject(PessoaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly bucketId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.bucketId;
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly pessoas = signal<Pessoa[]>([]);
  protected readonly cadastroRapido = signal<CadastroRapido | null>(null);
  private readonly categoriaSelecionada = signal('');
  private readonly subcategoriaOriginal = signal('');
  private readonly pessoaOriginal = signal('');
  protected readonly categoriasDisponiveis = computed(() =>
    this.categorias().filter((categoria) =>
      categoria.tipo === 'DESPESA'
      && (categoria.ativo || categoria.id === this.categoriaSelecionada()),
    ),
  );
  protected readonly subcategoriasDisponiveis = computed(() =>
    this.categoriasDisponiveis()
      .find((categoria) => categoria.id === this.categoriaSelecionada())
      ?.subcategorias
      .filter((subcategoria) =>
        subcategoria.ativo || subcategoria.id === this.subcategoriaOriginal(),
      ) ?? [],
  );
  protected readonly pessoasDisponiveis = computed(() =>
    this.pessoas().filter((pessoa) => pessoa.ativo || pessoa.id === this.pessoaOriginal()),
  );
  protected readonly form = this.fb.nonNullable.group({
    categoriaId: ['', Validators.required],
    subcategoriaId: ['', Validators.required],
    responsavelPessoaId: [''],
    valorMensal: [0, [Validators.required, Validators.min(0.01)]],
  });

  constructor() {
    const dadosBucket$ = this.bucketId
      ? this.bucketService.buscarPorId(this.bucketId)
      : null;
    const requests = {
      categorias: this.categoriaService.listar(),
      pessoas: this.pessoaService.listar(),
    };
    if (dadosBucket$) {
      forkJoin({ ...requests, bucket: dadosBucket$ }).subscribe({
        next: ({ categorias, pessoas, bucket }) => {
          this.preencherDados(categorias, pessoas);
          this.form.setValue({
            categoriaId: bucket.categoriaId,
            subcategoriaId: bucket.subcategoriaId,
            responsavelPessoaId: bucket.responsavelPessoaId ?? '',
            valorMensal: bucket.valorMensal,
          });
          this.categoriaSelecionada.set(bucket.categoriaId);
          this.subcategoriaOriginal.set(bucket.subcategoriaId);
          this.pessoaOriginal.set(bucket.responsavelPessoaId ?? '');
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => this.falhaAoCarregar(err),
      });
    } else {
      forkJoin(requests).subscribe({
        next: ({ categorias, pessoas }) => {
          this.preencherDados(categorias, pessoas);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => this.falhaAoCarregar(err),
      });
    }
  }

  protected atualizarCategoria(event: Event): void {
    const categoriaId = (event.target as HTMLSelectElement).value;
    this.categoriaSelecionada.set(categoriaId);
    this.form.controls.categoriaId.setValue(categoriaId);
    this.form.controls.subcategoriaId.setValue('');
  }

  protected atualizarPessoa(event: Event): void {
    const responsavelPessoaId = (event.target as HTMLSelectElement).value;
    this.form.controls.responsavelPessoaId.setValue(responsavelPessoaId);
    this.pessoaOriginal.set(responsavelPessoaId);
  }

  protected abrirCadastroSubcategoria(): void {
    const categoria = this.categorias().find((item) => item.id === this.categoriaSelecionada());
    if (categoria) {
      this.cadastroRapido.set({ tipo: 'subcategoria', categoria });
    }
  }

  protected aoCriarCategoria(categoria: Categoria): void {
    this.cadastroRapido.set(null);
    this.categorias.update((categorias) => [...categorias, categoria]);
    this.categoriaSelecionada.set(categoria.id);
    this.form.patchValue({
      categoriaId: categoria.id,
      subcategoriaId: categoria.subcategorias[0]?.id ?? '',
    });
  }

  protected aoCriarSubcategoria(subcategoria: Subcategoria): void {
    this.cadastroRapido.set(null);
    this.categorias.update((categorias) =>
      categorias.map((categoria) =>
        categoria.id === subcategoria.categoriaId
          ? { ...categoria, subcategorias: [...categoria.subcategorias, subcategoria] }
          : categoria,
      ),
    );
    this.form.controls.subcategoriaId.setValue(subcategoria.id);
  }

  protected aoCriarPessoa(pessoa: Pessoa): void {
    this.cadastroRapido.set(null);
    this.pessoas.update((pessoas) => [...pessoas, pessoa]);
    this.form.controls.responsavelPessoaId.setValue(pessoa.id);
    this.pessoaOriginal.set(pessoa.id);
  }

  protected submit(): void {
    if (this.form.invalid || this.loading() || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: BucketRequest = {
      subcategoriaId: value.subcategoriaId,
      responsavelPessoaId: value.responsavelPessoaId || null,
      valorMensal: value.valorMensal,
    };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.bucketId
      ? this.bucketService.atualizar(this.bucketId, request)
      : this.bucketService.criar(request);
    operation.subscribe({
      next: () => this.router.navigate(['/home/buckets']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar o bucket.'));
        this.saving.set(false);
      },
    });
  }

  private preencherDados(categorias: Categoria[], pessoas: Pessoa[]): void {
    this.categorias.set(categorias);
    this.pessoas.set(pessoas);
    if (!this.bucketId) {
      const categoria = categorias.find((item) =>
        item.tipo === 'DESPESA' && item.ativo && item.subcategorias.some(
          (subcategoria) => subcategoria.ativo,
        ),
      );
      const subcategoria = categoria?.subcategorias.find(
        (item) => item.ativo,
      );
      if (categoria && subcategoria) {
        this.form.patchValue({ categoriaId: categoria.id, subcategoriaId: subcategoria.id });
        this.categoriaSelecionada.set(categoria.id);
      }
    }
  }

  private falhaAoCarregar(err: HttpErrorResponse): void {
    this.error.set(this.mensagemErro(err, 'Não foi possível carregar os dados do bucket.'));
    this.loading.set(false);
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
