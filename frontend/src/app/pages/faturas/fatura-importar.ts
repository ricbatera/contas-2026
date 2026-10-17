import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { FaturaImportacaoService, lerArquivoBase64 } from '../../core/fatura-importacao.service';
import { ImportacaoMonitorService } from '../../core/importacao-monitor.service';
import { mensagemErro } from './importacao-util';

export const TAMANHO_MAXIMO_PDF = 10 * 1024 * 1024;

@Component({
  selector: 'app-fatura-importar',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './fatura-importar.html',
  styleUrls: ['../cartoes/cartoes.css', './faturas.css', './importacao.css'],
})
export class FaturaImportar {
  private readonly importacaoService = inject(FaturaImportacaoService);
  private readonly monitor = inject(ImportacaoMonitorService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    senha: ['', [Validators.maxLength(100)]],
  });
  protected readonly arquivo = signal<File | null>(null);
  protected readonly arquivoErro = signal<string | null>(null);
  protected readonly senhaErro = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected selecionarArquivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    this.arquivoErro.set(null);
    this.error.set(null);
    if (arquivo && !this.ehPdf(arquivo)) {
      this.arquivo.set(null);
      this.arquivoErro.set('Selecione um arquivo PDF.');
      input.value = '';
      return;
    }
    if (arquivo && arquivo.size > TAMANHO_MAXIMO_PDF) {
      this.arquivo.set(null);
      this.arquivoErro.set('O PDF deve ter no máximo 10 MB.');
      input.value = '';
      return;
    }
    this.arquivo.set(arquivo);
  }

  protected tamanho(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  protected async enviar(): Promise<void> {
    const arquivo = this.arquivo();
    if (!arquivo) {
      this.arquivoErro.set('Selecione o PDF da fatura.');
      return;
    }
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.senhaErro.set(null);
    let conteudoBase64: string;
    try {
      conteudoBase64 = await lerArquivoBase64(arquivo);
    } catch {
      this.enviando.set(false);
      this.error.set('Não foi possível ler o arquivo selecionado.');
      return;
    }
    const senha = this.form.controls.senha.value.trim();
    this.importacaoService.importar({
      nomeArquivo: arquivo.name,
      conteudoBase64,
      senha: senha || null,
    }).subscribe({
      next: (importacao) => {
        this.enviando.set(false);
        // A leitura por IA segue no servidor; o monitor acompanha e avisa quando terminar.
        this.monitor.acompanhar({
          id: importacao.id,
          nomeArquivo: importacao.nomeArquivo,
          status: importacao.status,
          erro: importacao.erro,
          preSaidasPendentes: importacao.preSaidas.filter((item) => item.status === 'PENDENTE').length,
          atualizadoEm: null,
        });
        this.router.navigate(['/home/faturas/importacoes', importacao.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.enviando.set(false);
        const mensagem = mensagemErro(err, 'Não foi possível importar a fatura.');
        if (err.status === 422 && /senha/i.test(mensagem)) {
          this.senhaErro.set(mensagem);
        } else {
          this.error.set(mensagem);
        }
      },
    });
  }

  private ehPdf(arquivo: File): boolean {
    return arquivo.type === 'application/pdf' || arquivo.name.toLowerCase().endsWith('.pdf');
  }
}
