import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ProvedorIaForm } from './provedor-ia-form';

describe('ProvedorIaForm', () => {
  let http: HttpTestingController;

  function criar(id: string | null = null) {
    TestBed.configureTestingModule({
      imports: [ProvedorIaForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProvedorIaForm);
    fixture.detectChanges();
    return fixture;
  }

  function digitar(fixture: ReturnType<typeof criar>, seletor: string, valor: string) {
    const elemento: HTMLInputElement | HTMLSelectElement = fixture.nativeElement.querySelector(seletor);
    elemento.value = valor;
    elemento.dispatchEvent(new Event(elemento instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function botaoBuscar(fixture: ReturnType<typeof criar>): HTMLButtonElement {
    return fixture.nativeElement.querySelector('.modelo-campo > .secondary-button');
  }

  function abrirLista(fixture: ReturnType<typeof criar>) {
    if (!fixture.nativeElement.querySelector('#modelos-sugeridos')) {
      fixture.nativeElement.querySelector('.modelo-toggle').click();
      fixture.detectChanges();
    }
  }

  function sugestoes(fixture: ReturnType<typeof criar>): string[] {
    abrirLista(fixture);
    return Array.from(fixture.nativeElement.querySelectorAll('#modelos-sugeridos li[role="option"] span')).map(
      (item) => (item as HTMLElement).textContent!.trim(),
    );
  }

  afterEach(() => http.verify());

  it('fills the suggestions with the models returned by the provider', () => {
    const fixture = criar();
    digitar(fixture, 'input[formControlName="apiKey"]', 'sk-teste');
    botaoBuscar(fixture).click();

    const req = http.expectOne('/api/ia/provedores/modelos');
    expect(req.request.body).toEqual({ tipo: 'OPENAI', baseUrl: null, apiKey: 'sk-teste', provedorId: null });
    req.flush({
      data: { modelos: [{ nome: 'gpt-5', nomeExibicao: 'gpt-5' }, { nome: 'o4-mini', nomeExibicao: 'o4-mini' }], erro: null },
    });
    fixture.detectChanges();

    expect(sugestoes(fixture)).toEqual(['gpt-5', 'o4-mini']);
    expect(fixture.nativeElement.textContent).toContain('2 modelos disponíveis');
  });

  it('asks for the API key before searching a new provider', () => {
    const fixture = criar();
    botaoBuscar(fixture).click();
    fixture.detectChanges();

    http.expectNone('/api/ia/provedores/modelos');
    expect(fixture.nativeElement.textContent).toContain('Informe a API key para buscar os modelos.');
  });

  it('uses the stored key when editing with the key field empty', () => {
    const fixture = criar('p1');
    http.expectOne('/api/ia/provedores/p1').flush({
      data: {
        id: 'p1', nome: 'Gemini', tipo: 'GEMINI', modelo: 'gemini-2.5-flash', baseUrl: null,
        possuiApiKey: true, apiKeyMascarada: 'AIz...1234', temperatura: null, maxTokens: null,
        timeoutSegundos: 60, padrao: false, ativo: true, criadoEm: null,
      },
    });
    fixture.detectChanges();
    botaoBuscar(fixture).click();

    const req = http.expectOne('/api/ia/provedores/modelos');
    expect(req.request.body).toEqual({ tipo: 'GEMINI', baseUrl: null, apiKey: null, provedorId: 'p1' });
    req.flush({ data: { modelos: [], erro: '[401] API key not valid' } });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('API key not valid');
  });

  it('clears the loaded models when the provider type changes', () => {
    const fixture = criar();
    digitar(fixture, 'input[formControlName="apiKey"]', 'sk-teste');
    botaoBuscar(fixture).click();
    http.expectOne('/api/ia/provedores/modelos').flush({
      data: { modelos: [{ nome: 'gpt-5', nomeExibicao: 'gpt-5' }], erro: null },
    });
    fixture.detectChanges();

    digitar(fixture, 'select[formControlName="tipo"]', 'OLLAMA');

    expect(sugestoes(fixture)).toEqual(['llama3.2', 'qwen2.5', 'mistral']);
  });

  it('filters the list while typing and keeps free text', () => {
    const fixture = criar();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[formControlName="modelo"]');
    digitar(fixture, 'input[formControlName="modelo"]', '4.1');

    expect(sugestoes(fixture)).toEqual(['gpt-4.1', 'gpt-4.1-mini']);

    digitar(fixture, 'input[formControlName="modelo"]', 'meu-modelo-custom');
    expect(fixture.nativeElement.textContent).toContain('Nenhum modelo encontrado');
    expect(input.value).toBe('meu-modelo-custom');
  });

  it('shows all models when opened by the toggle and selects with the keyboard', () => {
    const fixture = criar();
    digitar(fixture, 'input[formControlName="modelo"]', 'gpt-4o');
    input(fixture).dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(sugestoes(fixture)).toEqual(['gpt-4o-mini', 'gpt-4o', 'gpt-4.1', 'gpt-4.1-mini']);

    input(fixture).dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input(fixture).dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(input(fixture).value).toBe('gpt-4.1');
    expect(fixture.nativeElement.querySelector('#modelos-sugeridos')).toBeNull();
  });

  function input(fixture: ReturnType<typeof criar>): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[formControlName="modelo"]');
  }
});
