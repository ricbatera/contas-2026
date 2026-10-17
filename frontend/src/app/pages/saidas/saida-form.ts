import { CurrencyPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiResponse } from '../../core/auth.service';
import { Cartao, CartaoService } from '../../core/cartao.service';
import { Categoria, CategoriaService } from '../../core/categoria.service';
import {
  FORMAS_PAGAMENTO,
  FormaPagamento,
  ModoResponsabilidade,
  Saida,
  SaidaRequest,
  SaidaService,
} from '../../core/saida.service';
import { Pessoa, PessoaService } from '../../core/pessoa.service';

interface UserProfile {
  id: string;
  nome: string;
  sobrenome: string;
}

@Component({
  selector: 'app-saida-form',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './saida-form.html',
  styleUrls: ['../cartoes/cartoes.css', './saidas.css'],
})
export class SaidaForm {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly http = inject(HttpClient);
  private readonly cartaoService = inject(CartaoService);
  private readonly pessoaService = inject(PessoaService);
  private readonly categoriaService = inject(CategoriaService);
  private readonly saidaService = inject(SaidaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly formasAlternativas = FORMAS_PAGAMENTO.filter(
    (forma) => forma !== 'CARTAO_CREDITO',
  );
  protected readonly quantidades = Array.from({ length: 60 }, (_, index) => index + 1);
  protected readonly saidaId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.saidaId;
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dataError = signal<string | null>(null);
  protected readonly cartoes = signal<Cartao[]>([]);
  protected readonly pessoas = signal<Pessoa[]>([]);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly profile = signal<UserProfile | null>(null);

  protected readonly form = this.fb.group({
    descricao: ['', [Validators.required, Validators.maxLength(120)]],
    descricaoFatura: [''],
    contaFixa: [false],
    valorPorParcela: [0, [Validators.required, Validators.min(0.01)]],
    quantidadeParcelas: [1, [Validators.required, Validators.min(1), Validators.max(60)]],
    formaPagamento: ['CARTAO_CREDITO' as 'CARTAO_CREDITO' | 'OUTRO', Validators.required],
    pagamentoAlternativo: ['BOLETO' as Exclude<FormaPagamento, 'CARTAO_CREDITO'>, Validators.required],
    dataInicial: [''],
    primeiroVencimento: [''],
    cartaoId: ['', Validators.required],
    categoriaId: ['', Validators.required],
    subcategoriaId: ['', Validators.required],
    modoResponsabilidade: ['USUARIO' as ModoResponsabilidade, Validators.required],
    responsavelPessoaId: [''],
    partes: this.fb.array([this.novaParte()]),
  });

  constructor() {
    this.form.controls.formaPagamento.valueChanges.subscribe((forma) => this.aoAlterarForma(forma));
    this.form.controls.modoResponsabilidade.valueChanges.subscribe((modo) => this.aoAlterarModo(modo));
    this.form.controls.categoriaId.valueChanges.subscribe((categoriaId) => this.aoAlterarCategoria(categoriaId));
    this.aoAlterarForma(this.form.controls.formaPagamento.value);
    this.aoAlterarModo(this.form.controls.modoResponsabilidade.value);

    forkJoin({
      cartoes: this.cartaoService.listar(),
      pessoas: this.pessoaService.listar(),
      categorias: this.categoriaService.listar(),
      profile: this.http.get<ApiResponse<UserProfile>>('/api/usuarios/me'),
    }).subscribe({
      next: ({ cartoes, pessoas, categorias, profile }) => {
        this.cartoes.set(cartoes.filter((cartao) => cartao.ativo));
        this.pessoas.set(pessoas.filter((pessoa) => pessoa.ativo));
        this.categorias.set(
          categorias.filter(
            (categoria) =>
              categoria.ativo &&
              categoria.tipo === 'DESPESA' &&
              categoria.subcategorias.some((subcategoria) => subcategoria.ativo),
          ),
        );
        this.profile.set(profile.data);
        if (this.saidaId) {
          this.carregarSaida(this.saidaId);
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

  protected get partes(): FormArray {
    return this.form.controls.partes;
  }

  protected get subcategoriasDisponiveis() {
    const categoria = this.categorias().find((item) => item.id === this.form.controls.categoriaId.value);
    return categoria?.subcategorias.filter((subcategoria) => subcategoria.ativo) ?? [];
  }

  protected get valorMinhaParte(): number {
    const totalPessoasEmCentavos = this.partes.controls.reduce(
      (sum, parte) => sum + Math.round(Number(parte.get('valorPorParcela')?.value || 0) * 100),
      0,
    );
    const parcelaEmCentavos = Math.round(Number(this.form.controls.valorPorParcela.value || 0) * 100);
    return Math.max(0, parcelaEmCentavos - totalPessoasEmCentavos) / 100;
  }

  protected get somaPartesExcedeValor(): boolean {
    const somaEmCentavos = this.partes.controls.reduce(
      (total, parte) => total + Math.round(Number(parte.get('valorPorParcela')?.value || 0) * 100),
      0,
    );
    return somaEmCentavos > Math.round(Number(this.form.controls.valorPorParcela.value || 0) * 100);
  }

  protected pessoaJaSelecionada(id: string, index: number): boolean {
    const selecionadas = new Set(
      this.partes.controls
        .filter((_, selectedIndex) => selectedIndex !== index)
        .map((parte) => String(parte.get('pessoaId')?.value ?? ''))
        .filter(Boolean),
    );
    return selecionadas.has(id);
  }

  protected nomeForma(forma: FormaPagamento): string {
    return {
      CARTAO_CREDITO: 'Cartão de crédito',
      BOLETO: 'Boleto',
      DEBITO: 'Débito',
      PIX: 'Pix',
    }[forma];
  }

  protected descricaoResponsabilidade(): string {
    const modo = this.form.controls.modoResponsabilidade.value;
    if (modo === 'PESSOA') {
      return 'O valor integral de cada parcela ficará sob responsabilidade da pessoa selecionada.';
    }
    if (modo === 'DIVIDIR') {
      return 'Os valores informados são por parcela; o restante fica sob sua responsabilidade.';
    }
    return `O valor integral ficará sob responsabilidade de ${this.profile()?.nome ?? 'você'}.`;
  }

  protected adicionarParte(): void {
    this.partes.push(this.novaParte());
  }

  protected removerParte(index: number): void {
    if (this.partes.length > 1) {
      this.partes.removeAt(index);
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.somaPartesExcedeValor || this.loading() || this.saving() || this.dataError()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const forma: FormaPagamento = value.formaPagamento === 'CARTAO_CREDITO'
      ? 'CARTAO_CREDITO'
      : value.pagamentoAlternativo;
    const modo = value.modoResponsabilidade;
    const request: SaidaRequest = {
      descricao: value.descricao.trim(),
      descricaoFatura: forma === 'CARTAO_CREDITO' ? value.descricaoFatura.trim() || null : null,
      contaFixa: value.contaFixa,
      valorPorParcela: Number(value.valorPorParcela),
      quantidadeParcelas: Number(value.quantidadeParcelas),
      formaPagamento: forma,
      dataInicial: forma === 'CARTAO_CREDITO' ? null : value.dataInicial || null,
      primeiroVencimento: forma === 'CARTAO_CREDITO' ? value.primeiroVencimento || null : null,
      cartaoId: forma === 'CARTAO_CREDITO' ? value.cartaoId || null : null,
      categoriaId: value.categoriaId,
      subcategoriaId: value.subcategoriaId,
      modoResponsabilidade: modo,
      responsavelPessoaId: modo === 'PESSOA' ? value.responsavelPessoaId || null : null,
      partes:
        modo === 'DIVIDIR'
          ? value.partes.map((parte) => ({
              pessoaId: parte.pessoaId,
              valorPorParcela: Number(parte.valorPorParcela),
            }))
          : [],
    };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.saidaId
      ? this.saidaService.atualizar(this.saidaId, request)
      : this.saidaService.criar(request);
    operation.subscribe({
      next: () => this.router.navigate(['/home/saidas']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar a saída.'));
        this.saving.set(false);
      },
    });
  }

  private carregarSaida(id: string): void {
    this.saidaService.buscarPorId(id).subscribe({
      next: (saida) => {
        this.preencherFormulario(saida);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar a saída.'));
        this.loading.set(false);
      },
    });
  }

  private preencherFormulario(saida: Saida): void {
    this.adicionarReferenciasHistoricas(saida);
    const modo = saida.modoResponsabilidade;
    this.form.patchValue({
      descricao: saida.descricao,
      descricaoFatura: saida.descricaoFatura ?? '',
      contaFixa: saida.contaFixa,
      valorPorParcela: saida.valorPorParcela,
      quantidadeParcelas: saida.quantidadeParcelas,
      formaPagamento: saida.formaPagamento === 'CARTAO_CREDITO' ? 'CARTAO_CREDITO' : 'OUTRO',
      pagamentoAlternativo: saida.formaPagamento === 'CARTAO_CREDITO' ? 'BOLETO' : saida.formaPagamento,
      dataInicial: saida.dataInicial ?? '',
      primeiroVencimento: saida.primeiroVencimento ?? '',
      cartaoId: saida.cartaoId ?? '',
      categoriaId: saida.categoriaId,
      subcategoriaId: saida.subcategoriaId,
      modoResponsabilidade: modo,
      responsavelPessoaId: saida.responsavelPessoaId ?? '',
    });
    if (modo === 'DIVIDIR') {
      this.partes.clear();
      for (const item of saida.responsabilidades.filter((responsavel) => responsavel.pessoaId)) {
        this.partes.push(
          this.novaParte(item.pessoaId!, item.valorPorParcela),
        );
      }
      if (this.partes.length === 0) {
        this.partes.push(this.novaParte());
      }
    }
  }

  private adicionarReferenciasHistoricas(saida: Saida): void {
    if (saida.cartaoId && !this.cartoes().some((cartao) => cartao.id === saida.cartaoId)) {
      this.cartoes.update((cartoes) => [
        ...cartoes,
        {
          id: saida.cartaoId!,
          nome: saida.cartaoNome ?? 'Cartão inativo',
          banco: '',
          bandeira: 'OUTRO',
          ultimosDigitos: '',
          cor: '',
          criadoEm: '',
          tipo: 'PRINCIPAL',
          virtual: false,
          cartaoPrincipalId: null,
          ativo: false,
        },
      ]);
    }
    const categoriaAtual = this.categorias().find((categoria) => categoria.id === saida.categoriaId);
    if (!categoriaAtual) {
      this.categorias.update((categorias) => [
        ...categorias,
        {
          id: saida.categoriaId,
          nome: saida.categoriaNome,
          tipo: 'DESPESA',
          ativo: false,
          padrao: false,
          criadoEm: null,
          subcategorias: [{
            id: saida.subcategoriaId,
            nome: saida.subcategoriaNome,
            ativo: false,
            padrao: false,
            categoriaId: saida.categoriaId,
            categoriaNome: saida.categoriaNome,
            criadoEm: null,
          }],
        },
      ]);
    } else if (!categoriaAtual.subcategorias.some((item) => item.id === saida.subcategoriaId)) {
      this.categorias.update((categorias) =>
        categorias.map((categoria) =>
          categoria.id === saida.categoriaId
            ? {
                ...categoria,
                subcategorias: [
                  ...categoria.subcategorias,
                  {
                    id: saida.subcategoriaId,
                    nome: saida.subcategoriaNome,
                    ativo: false,
                    padrao: false,
                    categoriaId: saida.categoriaId,
                    categoriaNome: saida.categoriaNome,
                    criadoEm: null,
                  },
                ],
              }
            : categoria,
        ),
      );
    }
    const pessoasInativas = saida.responsabilidades
      .filter((item) => item.pessoaId && !this.pessoas().some((pessoa) => pessoa.id === item.pessoaId))
      .map((item) => ({
        id: item.pessoaId!,
        nome: item.nome,
        ativo: false,
        criadoEm: null,
      }));
    if (pessoasInativas.length > 0) {
      this.pessoas.update((pessoas) => [...pessoas, ...pessoasInativas]);
    }
  }

  private aplicarCategoriaPadrao(): void {
    const categoria = this.categorias().find((item) => item.padrao);
    if (!categoria) {
      return;
    }
    this.form.controls.categoriaId.setValue(categoria.id);
    const subcategoria = categoria.subcategorias.find((item) => item.ativo && item.padrao)
      ?? categoria.subcategorias.find((item) => item.ativo);
    this.form.controls.subcategoriaId.setValue(subcategoria?.id ?? '');
  }

  private aoAlterarCategoria(categoriaId: string | null): void {
    const categoria = this.categorias().find((item) => item.id === categoriaId);
    const subcategoria = categoria?.subcategorias.find((item) => item.ativo && item.padrao)
      ?? categoria?.subcategorias.find((item) => item.ativo);
    this.form.controls.subcategoriaId.setValue(subcategoria?.id ?? '');
  }

  private aoAlterarForma(forma: 'CARTAO_CREDITO' | 'OUTRO' | null): void {
    if (forma === 'CARTAO_CREDITO') {
      this.form.controls.cartaoId.setValidators([Validators.required]);
      this.form.controls.primeiroVencimento.setValidators([Validators.required]);
      this.form.controls.dataInicial.clearValidators();
      this.form.controls.dataInicial.setValue('');
    } else {
      this.form.controls.cartaoId.clearValidators();
      this.form.controls.cartaoId.setValue('');
      this.form.controls.primeiroVencimento.clearValidators();
      this.form.controls.primeiroVencimento.setValue('');
      this.form.controls.dataInicial.setValidators([Validators.required]);
      this.form.controls.descricaoFatura.setValue('');
    }
    this.form.controls.cartaoId.updateValueAndValidity();
    this.form.controls.primeiroVencimento.updateValueAndValidity();
    this.form.controls.dataInicial.updateValueAndValidity();
  }

  private aoAlterarModo(modo: ModoResponsabilidade | null): void {
    if (modo === 'PESSOA') {
      this.form.controls.responsavelPessoaId.setValidators([Validators.required]);
    } else {
      this.form.controls.responsavelPessoaId.clearValidators();
      this.form.controls.responsavelPessoaId.setValue('');
    }
    if (modo === 'DIVIDIR' && this.partes.length === 0) {
      this.partes.push(this.novaParte());
    } else if (modo !== 'DIVIDIR') {
      this.partes.clear();
    }
    this.form.controls.responsavelPessoaId.updateValueAndValidity();
  }

  private novaParte(pessoaId = '', valorPorParcela = 0) {
    return this.fb.group({
      pessoaId: [pessoaId, Validators.required],
      valorPorParcela: [valorPorParcela, [Validators.required, Validators.min(0.01)]],
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
