import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProvedorIa, ProvedorIaService, TesteIa } from '../../core/provedor-ia.service';
import { INFO_PROVEDOR, mensagemErroHttp } from './provedores-ia';

@Component({
  selector: 'app-ia-teste',
  imports: [DecimalPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './ia-teste.html',
  styleUrls: ['../cartoes/cartoes.css', '../contas/contas.css', './ia.css'],
})
export class IaTeste implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProvedorIaService);
  private readonly route = inject(ActivatedRoute);

  protected readonly provedores = signal<ProvedorIa[]>([]);
  protected readonly loading = signal(true);
  protected readonly testing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultado = signal<TesteIa | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    provedorId: ['', [Validators.required]],
    prompt: ['', [Validators.maxLength(4000)]],
  });

  ngOnInit(): void {
    this.service.listar().subscribe({
      next: (provedores) => {
        this.provedores.set(provedores);
        const solicitado = this.route.snapshot.queryParamMap.get('provedor');
        const inicial =
          provedores.find((p) => p.id === solicitado) ?? provedores.find((p) => p.padrao) ?? provedores[0];
        if (inicial) {
          this.form.controls.provedorId.setValue(inicial.id);
        }
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErroHttp(err, 'Não foi possível carregar os provedores de IA.'));
        this.loading.set(false);
      },
    });
  }

  protected provedorSelecionado(): ProvedorIa | undefined {
    return this.provedores().find((p) => p.id === this.form.controls.provedorId.value);
  }

  protected descricao(provedor: ProvedorIa): string {
    return `${provedor.nome} · ${INFO_PROVEDOR[provedor.tipo].nome} · ${provedor.modelo}`;
  }

  protected testar(): void {
    if (this.form.invalid || this.testing()) {
      this.form.markAllAsTouched();
      return;
    }

    const { provedorId, prompt } = this.form.getRawValue();
    this.testing.set(true);
    this.error.set(null);
    this.resultado.set(null);
    this.service.testar(provedorId, prompt.trim() || null).subscribe({
      next: (resultado) => {
        this.resultado.set(resultado);
        this.testing.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErroHttp(err, 'Não foi possível executar o teste.'));
        this.testing.set(false);
      },
    });
  }
}
