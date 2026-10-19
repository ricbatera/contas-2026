import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ComprovanteEnviar } from './comprovante-enviar';

describe('ComprovanteEnviar', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ComprovanteEnviar],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    URL.createObjectURL = vi.fn(() => 'blob:previa');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => http.verify());

  function selecionar(element: HTMLElement, arquivo: File, camera = true): void {
    const seletor = camera ? 'input[capture="environment"]' : 'input[type="file"]:not([capture])';
    const input = element.querySelector<HTMLInputElement>(seletor)!;
    Object.defineProperty(input, 'files', { value: [arquivo], configurable: true });
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  it('offers the camera and the gallery', () => {
    const fixture = TestBed.createComponent(ComprovanteEnviar);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const camera = element.querySelector<HTMLInputElement>('input[capture="environment"]')!;
    expect(camera.accept).toBe('image/*');
    expect(element.querySelector('input[type="file"]:not([capture])')).not.toBeNull();
    expect(element.textContent).toContain('Tirar foto');
    expect(element.textContent).toContain('Escolher imagem');
  });

  it('rejects files that are not images', () => {
    const fixture = TestBed.createComponent(ComprovanteEnviar);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    selecionar(element, new File(['%PDF'], 'fatura.pdf', { type: 'application/pdf' }), false);
    fixture.detectChanges();

    expect(element.textContent).toContain('Selecione uma foto do comprovante.');
    expect(element.querySelector('.comprovante-previa')).toBeNull();
  });

  it('previews the photo, sends it, and opens the review', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ComprovanteEnviar);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    selecionar(element, new File(['foto'], 'comprovante.jpg', { type: 'image/jpeg' }));
    fixture.detectChanges();
    expect(element.querySelector<HTMLImageElement>('.comprovante-previa img')!.src).toBe('blob:previa');

    const botao = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes('Ler comprovante'))!;
    botao.click();

    // jsdom não redimensiona imagens; o JPEG original é enviado.
    const req = await vi.waitFor(() => http.expectOne('/api/comprovantes'));
    expect(req.request.body).toEqual({ nomeArquivo: 'comprovante.jpg', conteudoBase64: btoa('foto') });
    req.flush({ data: { id: 'comp-1' } });

    expect(navigate).toHaveBeenCalledWith(['/home/comprovantes', 'comp-1']);
  });
});
