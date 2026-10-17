import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ProvedorIaRequest,
  ProvedorIaService,
  TIPOS_PROVEDOR_IA,
  TipoProvedorIa,
} from '../../core/provedor-ia.service';
import { INFO_PROVEDOR, mensagemErroHttp } from './provedores-ia';

@Component({
  selector: 'app-provedor-ia-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './provedor-ia-form.html',
  styleUrls: ['../cartoes/cartoes.css', '../contas/contas.css', './ia.css'],
})
export class ProvedorIaForm {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProvedorIaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly tipos = TIPOS_PROVEDOR_IA;
  protected readonly provedorId = this.route.snapshot.paramMap.get('id');
  protected readonly editing = !!this.provedorId;
  protected readonly loading = signal(this.editing);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Indica se o provedor em edição já tem chave gravada (campo vazio = manter). */
  protected readonly possuiApiKey = signal(false);
  protected readonly apiKeyMascarada = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(50)]],
    tipo: ['OPENAI' as TipoProvedorIa, [Validators.required]],
    modelo: ['', [Validators.required, Validators.maxLength(100)]],
    baseUrl: ['', [Validators.maxLength(255), Validators.pattern(/^$|^https?:\/\/.+/)]],
    apiKey: ['', [Validators.maxLength(500)]],
    temperatura: [null as number | null, [Validators.min(0), Validators.max(2)]],
    maxTokens: [null as number | null, [Validators.min(1), Validators.max(200000)]],
    timeoutSegundos: [60, [Validators.required, Validators.min(5), Validators.max(300)]],
    padrao: [false],
    ativo: [true],
  });

  private readonly tipoAtual = toSignal(this.form.controls.tipo.valueChanges, {
    initialValue: this.form.controls.tipo.value,
  });
  protected readonly info = computed(() => INFO_PROVEDOR[this.tipoAtual()]);
  protected readonly usaApiKey = computed(() => this.tipoAtual() !== 'OLLAMA');
  protected readonly exigeBaseUrl = computed(() => this.tipoAtual() === 'OLLAMA');

  constructor() {
    if (this.provedorId) {
      this.service.buscarPorId(this.provedorId).subscribe({
        next: (provedor) => {
          this.form.setValue({
            nome: provedor.nome,
            tipo: provedor.tipo,
            modelo: provedor.modelo,
            baseUrl: provedor.baseUrl ?? '',
            apiKey: '',
            temperatura: provedor.temperatura,
            maxTokens: provedor.maxTokens,
            timeoutSegundos: provedor.timeoutSegundos,
            padrao: provedor.padrao,
            ativo: provedor.ativo,
          });
          this.possuiApiKey.set(provedor.possuiApiKey);
          this.apiKeyMascarada.set(provedor.apiKeyMascarada);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(mensagemErroHttp(err, 'Não foi possível carregar o provedor.'));
          this.loading.set(false);
        },
      });
    }
  }

  protected nomeTipo(tipo: TipoProvedorIa): string {
    return INFO_PROVEDOR[tipo].nome;
  }

  protected submit(): void {
    if (this.form.invalid || this.loading() || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const baseUrl = value.baseUrl.trim() || null;
    const apiKey = this.usaApiKey() ? value.apiKey.trim() || null : null;
    if (this.exigeBaseUrl() && !baseUrl) {
      this.error.set('Informe a URL base do Ollama.');
      return;
    }
    if (this.usaApiKey() && !apiKey && !(this.editing && this.possuiApiKey())) {
      this.error.set('Informe a API key do provedor.');
      return;
    }

    const request: ProvedorIaRequest = {
      ...value,
      nome: value.nome.trim(),
      modelo: value.modelo.trim(),
      baseUrl,
      apiKey,
      temperatura: value.temperatura ?? null,
      maxTokens: value.maxTokens ?? null,
    };
    this.saving.set(true);
    this.error.set(null);
    const operation = this.provedorId
      ? this.service.atualizar(this.provedorId, request)
      : this.service.criar(request);

    operation.subscribe({
      next: () => this.router.navigate(['/home/ia/provedores']),
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErroHttp(err, 'Não foi possível salvar o provedor.'));
        this.saving.set(false);
      },
    });
  }
}
