import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FaturaImportar } from './fatura-importar';

const PROVEDOR_PADRAO = { id: 'ia-1', nome: 'OpenAI', padrao: true, ativo: true };

describe('FaturaImportar', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [FaturaImportar],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function criar(provedores: object[]) {
    const fixture = TestBed.createComponent(FaturaImportar);
    fixture.detectChanges();
    http.expectOne('/api/ia/provedores').flush({ data: provedores });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('enables the upload when there is an active default AI integration', () => {
    const element = criar([PROVEDOR_PADRAO]);

    expect(element.querySelector<HTMLInputElement>('input[type="file"]')!.disabled).toBe(false);
    expect(element.querySelector<HTMLInputElement>('input[type="password"]')!.disabled).toBe(false);
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
    expect(element.querySelector('app-aviso-ia')!.textContent).toBe('');
  });

  it('blocks the upload and links to the AI setup when there is no AI integration', () => {
    const element = criar([]);

    expect(element.querySelector<HTMLInputElement>('input[type="file"]')!.disabled).toBe(true);
    expect(element.querySelector<HTMLInputElement>('input[type="password"]')!.disabled).toBe(true);
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    expect(element.textContent).toContain('Você ainda não cadastrou nenhuma integração de IA.');
    expect(element.querySelector('a[href="/home/ia/provedores/novo"]')).not.toBeNull();
  });
});
