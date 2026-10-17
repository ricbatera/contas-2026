import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BANDEIRAS, Cartao, CartaoService, TIPOS_CARTAO } from '../../core/cartao.service';
import { DialogoRapido, mensagemErro } from './dialogo-rapido';

@Component({
  selector: 'app-cartao-rapido',
  imports: [DialogoRapido, ReactiveFormsModule],
  templateUrl: './cartao-rapido.html',
  styleUrls: ['../../pages/cartoes/cartoes.css', '../../pages/saidas/saidas.css', './cadastro-rapido.css'],
})
export class CartaoRapido {
  private readonly service = inject(CartaoService);

  readonly salvo = output<Cartao>();
  readonly cancelado = output<void>();
  protected readonly bandeiras = BANDEIRAS;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly principais = signal<Cartao[]>([]);
  protected readonly principaisError = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(50)]],
    banco: ['', [Validators.required, Validators.maxLength(100)]],
    bandeira: ['VISA' as (typeof BANDEIRAS)[number], [Validators.required]],
    ultimosDigitos: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    tipo: ['PRINCIPAL' as (typeof TIPOS_CARTAO)[number], [Validators.required]],
    virtual: [false],
    cartaoPrincipalId: [''],
  });

  constructor() {
    this.service.listarPrincipais().subscribe({
      next: (cartoes) => this.principais.set(cartoes.filter((cartao) => cartao.ativo)),
      error: (err: HttpErrorResponse) =>
        this.principaisError.set(mensagemErro(err, 'Não foi possível carregar os cartões principais.')),
    });
    this.form.controls.tipo.valueChanges.subscribe((tipo) => {
      const control = this.form.controls.cartaoPrincipalId;
      if (tipo === 'ADICIONAL') {
        control.setValidators([Validators.required]);
      } else {
        control.clearValidators();
        control.setValue('');
      }
      control.updateValueAndValidity();
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.service
      .criar({
        ...value,
        nome: value.nome.trim(),
        banco: value.banco.trim(),
        cor: '#1e6fd0',
        ativo: true,
        cartaoPrincipalId: value.tipo === 'ADICIONAL' ? value.cartaoPrincipalId : null,
      })
      .subscribe({
        next: (cartao) => this.salvo.emit(cartao),
        error: (err: HttpErrorResponse) => {
          this.error.set(mensagemErro(err, 'Não foi possível cadastrar o cartão.'));
          this.saving.set(false);
        },
      });
  }
}
