import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ContaBancariaRequest,
  ContaBancariaService,
  TIPOS_CONTA,
} from '../../core/conta-bancaria.service';
import { CampoCalculadora } from '../../shared/calculadora/campo-calculadora';

const NOMES_TIPO: Record<(typeof TIPOS_CONTA)[number], string> = {
  CORRENTE: 'Conta corrente',
  POUPANCA: 'Poupança',
  CARTEIRA: 'Carteira',
  INVESTIMENTO: 'Investimento',
  OUTRO: 'Outro',
};

@Component({
  selector: 'app-conta-form',
  imports: [CampoCalculadora, CurrencyPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './conta-form.html',
  styleUrls: ['../cartoes/cartoes.css', './contas.css'],
})
export class ContaForm {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContaBancariaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly tipos = TIPOS_CONTA;
  protected readonly cardId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.cardId;
  protected readonly loading = signal(this.editing);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(50)]],
    banco: ['', [Validators.required, Validators.maxLength(100)]],
    tipoConta: ['CORRENTE' as (typeof TIPOS_CONTA)[number], [Validators.required]],
    agencia: ['', [Validators.maxLength(10)]],
    numeroConta: ['', [Validators.maxLength(20)]],
    saldoInicial: [0, [Validators.required, Validators.min(0)]],
    cor: ['#1e6fd0', [Validators.pattern(/^$|^#[0-9A-Fa-f]{6}$/)]],
    ativo: [true],
  });

  constructor() {
    if (this.cardId) {
      this.service.buscarPorId(this.cardId).subscribe({
        next: (conta) => {
          this.form.setValue({
            nome: conta.nome,
            banco: conta.banco,
            tipoConta: conta.tipoConta,
            agencia: conta.agencia ?? '',
            numeroConta: conta.numeroConta ?? '',
            saldoInicial: conta.saldoInicial,
            cor: conta.cor ?? '',
            ativo: conta.ativo,
          });
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(this.mensagemErro(err, 'Não foi possível carregar os dados da conta.'));
          this.loading.set(false);
        },
      });
    }
  }

  protected tipoConta(tipo: (typeof TIPOS_CONTA)[number]): string {
    return NOMES_TIPO[tipo];
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
    const request: ContaBancariaRequest = {
      ...value,
      agencia: value.agencia || null,
      numeroConta: value.numeroConta || null,
      cor: value.cor || null,
    };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.cardId
      ? this.service.atualizar(this.cardId, request)
      : this.service.criar(request);

    operation.subscribe({
      next: () => this.router.navigate(['/home/contas']),
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível salvar a conta.'));
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
