import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { IaTeste } from './ia-teste';

describe('IaTeste', () => {
  let http: HttpTestingController;

  const provedores = [
    { id: 'p1', nome: 'OpenAI', tipo: 'OPENAI', modelo: 'gpt-4o-mini', padrao: true },
    { id: 'p2', nome: 'Local', tipo: 'OLLAMA', modelo: 'llama3.2', padrao: false },
  ];

  function criar(queryParams: Record<string, string> = {}) {
    registerLocaleData(localePtBr);
    TestBed.configureTestingModule({
      imports: [IaTeste],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(IaTeste);
    fixture.detectChanges();
    http.expectOne('/api/ia/provedores').flush({ data: provedores });
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('preselects the provider from the query string', () => {
    const fixture = criar({ provedor: 'p2' });
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    expect(select.value).toBe('p2');
  });

  it('shows the model response after a successful test', () => {
    const fixture = criar();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/ia/provedores/p1/testar');
    expect(req.request.body).toEqual({ prompt: null });
    req.flush({
      data: {
        sucesso: true, prompt: 'x', resposta: 'OK', modelo: 'gpt-4o-mini',
        latenciaMs: 1234, tokensEntrada: 10, tokensSaida: 1, erro: null,
      },
    });
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Integração funcionando');
    expect(texto).toContain('OK');
    expect(texto).toContain('1.234 ms');
  });

  it('shows the provider error when the test fails', () => {
    const fixture = criar();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    http.expectOne('/api/ia/provedores/p1/testar').flush({
      data: {
        sucesso: false, prompt: 'x', resposta: null, modelo: 'gpt-4o-mini',
        latenciaMs: 300, tokensEntrada: null, tokensSaida: null, erro: 'AuthenticationException: invalid key',
      },
    });
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Falha na integração');
    expect(texto).toContain('invalid key');
  });
});
