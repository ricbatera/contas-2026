import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { Cartao, CartaoService } from '../../core/cartao.service';
import { Categoria, CategoriaService } from '../../core/categoria.service';
import {
  calcularVencimento,
  Comprovante,
  ComprovanteEdicaoRequest,
  ComprovanteService,
  ModalidadeComprovante,
} from '../../core/comprovante.service';
import { CampoCalculadora } from '../../shared/calculadora/campo-calculadora';
import { mensagemErro } from '../faturas/importacao-util';
import { ROTULO_STATUS_COMPROVANTE, rotuloModalidade } from './comprovantes-util';

@Component({
  selector: 'app-comprovante-revisao',
  imports: [CampoCalculadora, CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './comprovante-revisao.html',
  styleUrls: ['../cartoes/cartoes.css', '../faturas/importacao.css', './comprovantes.css'],
})
export class ComprovanteRevisao implements OnInit {
  private readonly service = inject(ComprovanteService);
  private readonly cartaoService = inject(CartaoService);
  private readonly categoriaService = inject(CategoriaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly comprovante = signal<Comprovante | null>(null);
  protected readonly cartoes = signal<Cartao[]>([]);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly imagemUrl = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly ocupado = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly mensagem = signal<string | null>(null);
  protected readonly confirmandoExclusao = signal(false);
  protected readonly parcelas = Array.from({ length: 60 }, (_, indice) => indice + 1);
  protected readonly rotuloStatus = ROTULO_STATUS_COMPROVANTE;
  protected readonly rotuloModalidade = rotuloModalidade;

  protected readonly form = this.fb.group({
    modalidade: this.fb.nonNullable.control<ModalidadeComprovante>('CREDITO'),
    descricao: ['', [Validators.maxLength(120)]],
    cartaoId: [''],
    dataCompra: [''],
    valorTotal: this.fb.control<number | null>(null, [Validators.min(0.01)]),
    quantidadeParcelas: this.fb.nonNullable.control(1),
    primeiroVencimento: [''],
    categoriaId: [''],
    subcategoriaId: [''],
  });

  /** getRawValue inclui os campos desabilitados (comprovante já aprovado ou rejeitado). */
  private readonly valores = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() });
  protected readonly pendente = computed(() => this.comprovante()?.status === 'PENDENTE');
  protected readonly debito = computed(() => this.valores().modalidade === 'DEBITO');
  protected readonly valorParcela = computed(() => {
    const { valorTotal, quantidadeParcelas } = this.valores();
    const parcelas = this.debito() ? 1 : Number(quantidadeParcelas) || 1;
    return valorTotal ? Math.round((Number(valorTotal) / parcelas) * 100) / 100 : null;
  });
  protected readonly categoriasDespesa = computed(() =>
    this.categorias().filter((categoria) => categoria.tipo === 'DESPESA' && categoria.ativo));
  protected readonly subcategorias = computed(() =>
    this.categorias()
      .find((categoria) => categoria.id === this.valores().categoriaId)
      ?.subcategorias.filter((subcategoria) => subcategoria.ativo) ?? []);
  /** Cartões ativos, mais o selecionado (que pode ter sido inativado depois). */
  protected readonly cartoesDisponiveis = computed(() =>
    this.cartoes().filter((cartao) => cartao.ativo || cartao.id === this.valores().cartaoId));
  protected readonly faltando = computed(() => {
    const valor = this.valores();
    const faltam: string[] = [];
    if (!valor.descricao?.trim()) faltam.push('descrição');
    if (!valor.dataCompra) faltam.push('data da compra');
    if (!valor.valorTotal) faltam.push('valor');
    if (!this.debito() && !valor.cartaoId) faltam.push('cartão');
    if (!this.debito() && !valor.primeiroVencimento) faltam.push('vencimento da 1ª parcela');
    if (!valor.categoriaId) faltam.push('categoria');
    if (!valor.subcategoriaId) faltam.push('subcategoria');
    return faltam;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberarImagem());
    // Trocar cartão ou data recalcula o vencimento, quando o cartão tem os dias da fatura.
    this.form.controls.cartaoId.valueChanges.subscribe(() => this.recalcularVencimento());
    this.form.controls.dataCompra.valueChanges.subscribe(() => this.recalcularVencimento());
    this.form.controls.categoriaId.valueChanges.subscribe(() =>
      this.form.controls.subcategoriaId.setValue('', { emitEvent: true }));
  }

  ngOnInit(): void {
    forkJoin({
      comprovante: this.service.buscarPorId(this.id),
      cartoes: this.cartaoService.listar(),
      categorias: this.categoriaService.listar(),
    }).subscribe({
      next: ({ comprovante, cartoes, categorias }) => {
        this.cartoes.set(cartoes);
        this.categorias.set(categorias);
        this.aplicar(comprovante);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErro(err, 'Não foi possível carregar o comprovante.'));
        this.loading.set(false);
      },
    });
    this.service.baixarImagem(this.id).subscribe({
      next: (imagem) => this.imagemUrl.set(URL.createObjectURL(imagem)),
      error: () => this.imagemUrl.set(null),
    });
  }

  protected rotuloCartao(cartao: Cartao): string {
    return `${cartao.nome} · final ${cartao.ultimosDigitos}${cartao.tipo === 'ADICIONAL' ? ' (adicional)' : ''}`;
  }

  protected salvar(): void {
    this.executar('salvar', this.gravar(), 'Alterações salvas.');
  }

  protected aprovar(): void {
    this.executar('aprovar', this.gravar().pipe(switchMap(() => this.service.aprovar(this.id))),
      'Comprovante aprovado: a saída foi criada.');
  }

  protected rejeitar(): void {
    this.executar('rejeitar', this.service.rejeitar(this.id), 'Comprovante rejeitado.');
  }

  protected reabrir(): void {
    this.executar('reabrir', this.service.reabrir(this.id), 'Comprovante reaberto para revisão.');
  }

  protected reler(): void {
    this.executar('reler', this.service.reler(this.id), 'Comprovante lido novamente.');
  }

  protected excluir(): void {
    this.ocupado.set('excluir');
    this.error.set(null);
    this.service.excluir(this.id).subscribe({
      next: () => this.router.navigate(['/home/comprovantes']),
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(null);
        this.confirmandoExclusao.set(false);
        this.error.set(mensagemErro(err, 'Não foi possível excluir o comprovante.'));
      },
    });
  }

  private executar(acao: string, operacao: Observable<Comprovante | null>, sucesso: string): void {
    if (this.ocupado()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.ocupado.set(acao);
    this.error.set(null);
    this.mensagem.set(null);
    operacao.subscribe({
      next: (comprovante) => {
        if (comprovante) {
          this.aplicar(comprovante);
        }
        this.mensagem.set(sucesso);
        this.ocupado.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErro(err, 'Não foi possível concluir a ação.'));
        this.ocupado.set(null);
      },
    });
  }

  /** Grava só se o usuário mudou algo; senão segue com o que já está no servidor. */
  private gravar(): Observable<Comprovante | null> {
    if (!this.form.dirty) {
      return of(null);
    }
    const valor = this.form.getRawValue();
    const request: ComprovanteEdicaoRequest = {
      modalidade: valor.modalidade,
      descricao: valor.descricao?.trim() || null,
      cartaoId: valor.cartaoId || null,
      categoriaId: valor.categoriaId || null,
      subcategoriaId: valor.subcategoriaId || null,
      dataCompra: valor.dataCompra || null,
      valorTotal: valor.valorTotal ? Number(valor.valorTotal) : null,
      quantidadeParcelas: valor.modalidade === 'DEBITO' ? 1 : Number(valor.quantidadeParcelas),
      primeiroVencimento: valor.modalidade === 'DEBITO' ? null : valor.primeiroVencimento || null,
    };
    return this.service.atualizar(this.id, request);
  }

  private aplicar(comprovante: Comprovante): void {
    this.comprovante.set(comprovante);
    this.form.reset({
      modalidade: comprovante.modalidade,
      descricao: comprovante.descricao ?? '',
      cartaoId: comprovante.cartaoId ?? '',
      dataCompra: comprovante.dataCompra ?? '',
      valorTotal: comprovante.valorTotal,
      quantidadeParcelas: comprovante.quantidadeParcelas,
      primeiroVencimento: comprovante.primeiroVencimento ?? '',
      categoriaId: comprovante.categoriaId ?? '',
      subcategoriaId: comprovante.subcategoriaId ?? '',
    }, { emitEvent: false });
    if (comprovante.status === 'PENDENTE') {
      this.form.enable({ emitEvent: false });
    } else {
      this.form.disable({ emitEvent: false });
    }
    // Sem eventos nos controles (não recalcula vencimento nem limpa a subcategoria), mas o
    // formulário emite uma vez para atualizar o signal dos valores.
    this.form.updateValueAndValidity();
  }

  private recalcularVencimento(): void {
    const { cartaoId, dataCompra } = this.form.getRawValue();
    const cartao = this.cartoes().find((item) => item.id === cartaoId);
    const principal = cartao?.cartaoPrincipalId
      ? this.cartoes().find((item) => item.id === cartao.cartaoPrincipalId)
      : cartao;
    if (dataCompra && principal?.diaFechamento && principal.diaVencimento) {
      this.form.controls.primeiroVencimento.setValue(
        calcularVencimento(dataCompra, principal.diaFechamento, principal.diaVencimento));
    }
  }

  private liberarImagem(): void {
    const url = this.imagemUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
  }
}
