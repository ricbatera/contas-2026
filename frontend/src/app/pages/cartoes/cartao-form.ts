import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  BANDEIRAS,
  Cartao,
  CartaoRequest,
  CartaoService,
  TIPOS_CARTAO,
} from '../../core/cartao.service';

@Component({
  selector: 'app-cartao-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './cartao-form.html',
  styleUrls: ['./cartoes.css', './cartao-form.css'],
})
export class CartaoForm {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CartaoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly bandeiras = BANDEIRAS;
  protected readonly tipos = TIPOS_CARTAO;
  protected readonly cardId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.cardId;
  protected readonly loading = signal(this.editing);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly principais = signal<Cartao[]>([]);
  protected readonly principaisError = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(50)]],
    banco: ['', [Validators.required, Validators.maxLength(100)]],
    bandeira: ['VISA' as (typeof BANDEIRAS)[number], [Validators.required]],
    ultimosDigitos: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    cor: ['#1e6fd0', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    tipo: ['PRINCIPAL' as (typeof TIPOS_CARTAO)[number], [Validators.required]],
    virtual: [false],
    ativo: [true],
    cartaoPrincipalId: [''],
    diaFechamento: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(31)]),
    diaVencimento: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(31)]),
  });

  constructor() {
    this.carregarPrincipais();
    this.form.controls.tipo.valueChanges.subscribe(() => this.atualizarValidacaoPrincipal());
    if (this.cardId) {
      this.service.buscarPorId(this.cardId).subscribe({
        next: (cartao) => {
          this.form.setValue({
            nome: cartao.nome,
            banco: cartao.banco,
            bandeira: cartao.bandeira,
            ultimosDigitos: cartao.ultimosDigitos,
            cor: cartao.cor,
            tipo: cartao.tipo,
            virtual: cartao.virtual,
            ativo: cartao.ativo,
            cartaoPrincipalId: cartao.cartaoPrincipalId ?? '',
            diaFechamento: cartao.diaFechamento ?? null,
            diaVencimento: cartao.diaVencimento ?? null,
          });
          this.atualizarValidacaoPrincipal();
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(this.mensagemErro(err, 'Não foi possível carregar os dados do cartão.'));
          this.loading.set(false);
        },
      });
    }
  }

  protected escolherCor(event: Event): void {
    this.form.controls.cor.setValue((event.target as HTMLInputElement).value);
    this.form.controls.cor.markAsDirty();
  }

  protected submit(): void {
    if (this.form.invalid || this.loading() || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const principal = value.tipo === 'PRINCIPAL';
    const request: CartaoRequest = {
      ...value,
      cartaoPrincipalId: principal ? null : value.cartaoPrincipalId,
      // O adicional entra na fatura do principal e usa os dias dele.
      diaFechamento: principal ? value.diaFechamento : null,
      diaVencimento: principal ? value.diaVencimento : null,
    };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.cardId
      ? this.service.atualizar(this.cardId, request)
      : this.service.criar(request);

    operation.subscribe({
      next: () => this.router.navigate(['/home/cartoes']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar o cartão.'));
        this.saving.set(false);
      },
    });
  }

  protected nomePrincipal(id: string | null): string {
    const principal = this.principais().find((card) => card.id === id);
    return principal ? `${principal.nome} · ${principal.banco} · final ${principal.ultimosDigitos}` : '';
  }

  private carregarPrincipais(): void {
    this.service.listarPrincipais().subscribe({
      next: (cards) => this.principais.set(cards.filter((card) => card.id !== this.cardId)),
      error: (err: HttpErrorResponse) => {
        this.principaisError.set(this.mensagemErro(err, 'Não foi possível carregar os cartões principais.'));
      },
    });
  }

  private atualizarValidacaoPrincipal(): void {
    const control = this.form.controls.cartaoPrincipalId;
    if (this.form.controls.tipo.value === 'ADICIONAL') {
      control.setValidators([Validators.required]);
    } else {
      control.clearValidators();
      control.setValue('');
    }
    control.updateValueAndValidity();
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
