import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ComprovanteService } from '../../core/comprovante.service';
import { lerArquivoBase64 } from '../../core/fatura-importacao.service';
import { reduzirImagem } from '../../shared/imagem/reduzir-imagem';
import { mensagemErro } from '../faturas/importacao-util';

const TIPOS_ACEITOS_SEM_CONVERSAO = ['image/jpeg', 'image/png', 'image/webp'];
export const TAMANHO_MAXIMO_IMAGEM = 8 * 1024 * 1024;

@Component({
  selector: 'app-comprovante-enviar',
  imports: [RouterLink],
  templateUrl: './comprovante-enviar.html',
  styleUrls: ['../cartoes/cartoes.css', '../faturas/importacao.css', './comprovantes.css'],
})
export class ComprovanteEnviar {
  private readonly service = inject(ComprovanteService);
  private readonly router = inject(Router);

  protected readonly arquivo = signal<File | null>(null);
  protected readonly previa = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberarPrevia());
  }

  protected selecionar(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    input.value = '';
    this.error.set(null);
    if (!arquivo) {
      return;
    }
    if (!arquivo.type.startsWith('image/') && !/\.(jpe?g|png|webp|heic|heif)$/i.test(arquivo.name)) {
      this.error.set('Selecione uma foto do comprovante.');
      return;
    }
    this.liberarPrevia();
    this.arquivo.set(arquivo);
    this.previa.set(URL.createObjectURL(arquivo));
  }

  protected descartar(): void {
    this.liberarPrevia();
    this.arquivo.set(null);
  }

  protected async enviar(): Promise<void> {
    const arquivo = this.arquivo();
    if (!arquivo || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    let conteudoBase64: string;
    try {
      conteudoBase64 = await lerArquivoBase64(await this.preparar(arquivo));
    } catch {
      this.enviando.set(false);
      this.error.set('Não foi possível ler a imagem. Tente outra foto em JPEG ou PNG.');
      return;
    }
    this.service.enviar({ nomeArquivo: arquivo.name || 'comprovante.jpg', conteudoBase64 }).subscribe({
      next: (comprovante) => {
        this.enviando.set(false);
        this.router.navigate(['/home/comprovantes', comprovante.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(mensagemErro(err, 'Não foi possível enviar o comprovante.'));
      },
    });
  }

  /**
   * Reduz a foto antes de enviar. Se o navegador não conseguir redimensionar, envia o original,
   * desde que seja um formato aceito pelo servidor e caiba no limite.
   */
  private async preparar(arquivo: File): Promise<Blob> {
    try {
      return await reduzirImagem(arquivo);
    } catch (erro) {
      if (TIPOS_ACEITOS_SEM_CONVERSAO.includes(arquivo.type) && arquivo.size <= TAMANHO_MAXIMO_IMAGEM) {
        return arquivo;
      }
      throw erro;
    }
  }

  private liberarPrevia(): void {
    const previa = this.previa();
    if (previa) {
      URL.revokeObjectURL(previa);
      this.previa.set(null);
    }
  }
}
