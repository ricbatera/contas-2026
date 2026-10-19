import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ModeloIa,
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

  /** Modelos consultados no provedor; `null` enquanto não buscou (usa as sugestões fixas). */
  protected readonly modelosDisponiveis = signal<ModeloIa[] | null>(null);
  protected readonly carregandoModelos = signal(false);
  protected readonly erroModelos = signal<string | null>(null);
  protected readonly sugestoesModelo = computed<ModeloIa[]>(
    () => this.modelosDisponiveis() ?? this.info().modelos.map((nome) => ({ nome, nomeExibicao: nome })),
  );

  /** Combobox do modelo: texto livre com a lista de sugestões, filtrada só enquanto o usuário digita. */
  protected readonly listaModelosAberta = signal(false);
  protected readonly indiceModeloAtivo = signal(-1);
  private readonly filtrandoModelos = signal(false);
  private readonly textoModelo = toSignal(this.form.controls.modelo.valueChanges, {
    initialValue: this.form.controls.modelo.value,
  });
  protected readonly modelosFiltrados = computed<ModeloIa[]>(() => {
    const termo = this.filtrandoModelos() ? this.textoModelo().trim().toLowerCase() : '';
    return termo
      ? this.sugestoesModelo().filter(
          (m) => m.nome.toLowerCase().includes(termo) || m.nomeExibicao.toLowerCase().includes(termo),
        )
      : this.sugestoesModelo();
  });

  constructor() {
    this.form.controls.tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.modelosDisponiveis.set(null);
      this.erroModelos.set(null);
      this.fecharListaModelos();
    });

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

  protected abrirListaModelos(): void {
    this.filtrandoModelos.set(false);
    const atual = this.form.controls.modelo.value.trim();
    this.indiceModeloAtivo.set(this.sugestoesModelo().findIndex((m) => m.nome === atual));
    this.listaModelosAberta.set(true);
  }

  protected fecharListaModelos(): void {
    this.listaModelosAberta.set(false);
    this.indiceModeloAtivo.set(-1);
  }

  protected alternarListaModelos(): void {
    if (this.listaModelosAberta()) {
      this.fecharListaModelos();
    } else {
      this.abrirListaModelos();
    }
  }

  protected digitouModelo(): void {
    this.filtrandoModelos.set(true);
    this.indiceModeloAtivo.set(-1);
    this.listaModelosAberta.set(true);
  }

  protected selecionarModelo(modelo: ModeloIa): void {
    this.form.controls.modelo.setValue(modelo.nome);
    this.form.controls.modelo.markAsDirty();
    this.fecharListaModelos();
  }

  protected teclaModelo(evento: KeyboardEvent): void {
    const total = this.modelosFiltrados().length;
    switch (evento.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        evento.preventDefault();
        if (!this.listaModelosAberta()) {
          this.abrirListaModelos();
          return;
        }
        if (total === 0) {
          return;
        }
        const passo = evento.key === 'ArrowDown' ? 1 : -1;
        this.indiceModeloAtivo.update((i) => (i < 0 && passo < 0 ? total - 1 : (i + passo + total) % total));
        document.getElementById(`modelo-opcao-${this.indiceModeloAtivo()}`)?.scrollIntoView?.({ block: 'nearest' });
        return;
      }
      case 'Enter': {
        const ativo = this.modelosFiltrados()[this.indiceModeloAtivo()];
        if (this.listaModelosAberta() && ativo) {
          evento.preventDefault();
          this.selecionarModelo(ativo);
        }
        return;
      }
      case 'Escape':
        if (this.listaModelosAberta()) {
          evento.preventDefault();
          this.fecharListaModelos();
        }
        return;
    }
  }

  protected buscarModelos(): void {
    if (this.carregandoModelos()) {
      return;
    }
    const value = this.form.getRawValue();
    const baseUrl = value.baseUrl.trim() || null;
    const apiKey = this.usaApiKey() ? value.apiKey.trim() || null : null;
    if (this.form.controls.baseUrl.invalid) {
      this.erroModelos.set('Corrija a URL base antes de buscar os modelos.');
      return;
    }
    if (this.exigeBaseUrl() && !baseUrl) {
      this.erroModelos.set('Informe a URL base do Ollama para buscar os modelos.');
      return;
    }
    if (this.usaApiKey() && !apiKey && !(this.editing && this.possuiApiKey())) {
      this.erroModelos.set('Informe a API key para buscar os modelos.');
      return;
    }

    this.carregandoModelos.set(true);
    this.erroModelos.set(null);
    this.service
      .listarModelos({ tipo: value.tipo, baseUrl, apiKey, provedorId: this.provedorId })
      .subscribe({
        next: (resultado) => {
          this.carregandoModelos.set(false);
          if (resultado.erro) {
            this.erroModelos.set(`Não foi possível listar os modelos: ${resultado.erro}`);
          } else if (resultado.modelos.length === 0) {
            this.erroModelos.set('O provedor não retornou nenhum modelo.');
          } else {
            this.modelosDisponiveis.set(resultado.modelos);
            this.abrirListaModelos();
          }
        },
        error: (err: HttpErrorResponse) => {
          this.carregandoModelos.set(false);
          this.erroModelos.set(mensagemErroHttp(err, 'Não foi possível listar os modelos.'));
        },
      });
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
