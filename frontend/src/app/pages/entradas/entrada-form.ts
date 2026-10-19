import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Categoria, CategoriaService, Subcategoria } from '../../core/categoria.service';
import { ContaBancaria, ContaBancariaService } from '../../core/conta-bancaria.service';
import { Entrada, EntradaRequest, EntradaService } from '../../core/entrada.service';
import { CategoriaRapida } from '../../shared/cadastro-rapido/categoria-rapida';
import { SubcategoriaRapida } from '../../shared/cadastro-rapido/subcategoria-rapida';
import { CampoCalculadora } from '../../shared/calculadora/campo-calculadora';

type CadastroRapido = { tipo: 'categoria' } | { tipo: 'subcategoria'; categoria: Categoria };

@Component({
  selector: 'app-entrada-form',
  imports: [CampoCalculadora, CategoriaRapida, ReactiveFormsModule, RouterLink, SubcategoriaRapida],
  templateUrl: './entrada-form.html',
  styleUrls: [
    '../cartoes/cartoes.css',
    '../saidas/saidas.css',
    './entradas.css',
    '../../shared/cadastro-rapido/cadastro-rapido.css',
  ],
})
export class EntradaForm {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly categoriaService = inject(CategoriaService);
  private readonly contaService = inject(ContaBancariaService);
  private readonly entradaService = inject(EntradaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly entradaId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.entradaId;
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dataError = signal<string | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly contas = signal<ContaBancaria[]>([]);
  protected readonly cadastroRapido = signal<CadastroRapido | null>(null);

  protected readonly form = this.fb.group({
    descricao: ['', [Validators.required, Validators.maxLength(120)]],
    valor: [0, [Validators.required, Validators.min(0.01)]],
    recorrente: [false],
    dataInicial: ['', Validators.required],
    dataFinal: [''],
    categoriaId: ['', Validators.required],
    subcategoriaId: ['', Validators.required],
    contaBancariaId: [''],
  });

  constructor() {
    this.form.controls.categoriaId.valueChanges.subscribe((categoriaId) => this.aoAlterarCategoria(categoriaId));
    this.form.controls.recorrente.valueChanges.subscribe((recorrente) => {
      if (!recorrente) {
        this.form.controls.dataFinal.setValue('');
      }
    });

    forkJoin({
      categorias: this.categoriaService.listar(),
      contas: this.contaService.listar(),
    }).subscribe({
      next: ({ categorias, contas }) => {
        this.categorias.set(
          categorias.filter(
            (categoria) =>
              categoria.ativo &&
              categoria.tipo === 'RECEITA' &&
              categoria.subcategorias.some((subcategoria) => subcategoria.ativo),
          ),
        );
        this.contas.set(contas.filter((conta) => conta.ativo));
        if (this.entradaId) {
          this.carregarEntrada(this.entradaId);
        } else {
          this.aplicarCategoriaPadrao();
          this.loading.set(false);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.dataError.set(this.mensagemErro(err, 'Não foi possível carregar os dados do formulário.'));
        this.loading.set(false);
      },
    });
  }

  protected get subcategoriasDisponiveis(): Subcategoria[] {
    const categoria = this.categorias().find((item) => item.id === this.form.controls.categoriaId.value);
    return categoria?.subcategorias.filter((subcategoria) => subcategoria.ativo) ?? [];
  }

  protected get dataFinalAnterior(): boolean {
    const { dataInicial, dataFinal } = this.form.getRawValue();
    return !!dataInicial && !!dataFinal && dataFinal < dataInicial;
  }

  protected abrirCadastroSubcategoria(): void {
    const categoria = this.categorias().find((item) => item.id === this.form.controls.categoriaId.value);
    if (categoria) {
      this.cadastroRapido.set({ tipo: 'subcategoria', categoria });
    }
  }

  protected aoCriarCategoria(categoria: Categoria): void {
    this.cadastroRapido.set(null);
    this.categorias.update((categorias) => [...categorias, categoria]);
    // aoAlterarCategoria seleciona a subcategoria recém-criada.
    this.form.controls.categoriaId.setValue(categoria.id);
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

  protected submit(): void {
    if (this.form.invalid || this.dataFinalAnterior || this.loading() || this.saving() || this.dataError()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: EntradaRequest = {
      descricao: value.descricao.trim(),
      valor: Number(value.valor),
      recorrente: value.recorrente,
      dataInicial: value.dataInicial,
      dataFinal: value.recorrente ? value.dataFinal || null : null,
      categoriaId: value.categoriaId,
      subcategoriaId: value.subcategoriaId,
      contaBancariaId: value.contaBancariaId || null,
    };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.entradaId
      ? this.entradaService.atualizar(this.entradaId, request)
      : this.entradaService.criar(request);
    operation.subscribe({
      next: () => this.router.navigate(['/home/entradas']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar a entrada.'));
        this.saving.set(false);
      },
    });
  }

  private carregarEntrada(id: string): void {
    this.entradaService.buscarPorId(id).subscribe({
      next: (entrada) => {
        this.adicionarReferenciasHistoricas(entrada);
        this.form.patchValue({
          descricao: entrada.descricao,
          valor: entrada.valor,
          recorrente: entrada.recorrente,
          dataInicial: entrada.dataInicial,
          dataFinal: entrada.dataFinal ?? '',
          categoriaId: entrada.categoriaId,
          subcategoriaId: entrada.subcategoriaId,
          contaBancariaId: entrada.contaBancariaId ?? '',
        });
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar a entrada.'));
        this.loading.set(false);
      },
    });
  }

  /** Mantém selecionáveis as referências inativas que a entrada já usa. */
  private adicionarReferenciasHistoricas(entrada: Entrada): void {
    const subcategoriaHistorica: Subcategoria = {
      id: entrada.subcategoriaId,
      nome: entrada.subcategoriaNome,
      ativo: true,
      padrao: false,
      categoriaId: entrada.categoriaId,
      categoriaNome: entrada.categoriaNome,
      criadoEm: null,
    };
    const categoriaAtual = this.categorias().find((categoria) => categoria.id === entrada.categoriaId);
    if (!categoriaAtual) {
      this.categorias.update((categorias) => [
        ...categorias,
        {
          id: entrada.categoriaId,
          nome: entrada.categoriaNome,
          tipo: 'RECEITA',
          ativo: false,
          padrao: false,
          criadoEm: null,
          subcategorias: [subcategoriaHistorica],
        },
      ]);
    } else if (!categoriaAtual.subcategorias.some((item) => item.id === entrada.subcategoriaId && item.ativo)) {
      this.categorias.update((categorias) =>
        categorias.map((categoria) =>
          categoria.id === entrada.categoriaId
            ? {
                ...categoria,
                subcategorias: [
                  ...categoria.subcategorias.filter((item) => item.id !== entrada.subcategoriaId),
                  subcategoriaHistorica,
                ],
              }
            : categoria,
        ),
      );
    }
    if (entrada.contaBancariaId && !this.contas().some((conta) => conta.id === entrada.contaBancariaId)) {
      this.contas.update((contas) => [
        ...contas,
        {
          id: entrada.contaBancariaId!,
          nome: entrada.contaBancariaNome ?? 'Conta inativa',
          banco: '',
          tipoConta: 'OUTRO',
          agencia: null,
          numeroConta: null,
          saldoInicial: 0,
          cor: null,
          ativo: false,
          criadoEm: null,
        },
      ]);
    }
  }

  private aplicarCategoriaPadrao(): void {
    const categoria = this.categorias().find((item) => item.padrao);
    if (categoria) {
      this.form.controls.categoriaId.setValue(categoria.id);
    }
  }

  private aoAlterarCategoria(categoriaId: string | null): void {
    const categoria = this.categorias().find((item) => item.id === categoriaId);
    const subcategoria = categoria?.subcategorias.find((item) => item.ativo && item.padrao)
      ?? categoria?.subcategorias.find((item) => item.ativo);
    this.form.controls.subcategoriaId.setValue(subcategoria?.id ?? '');
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
