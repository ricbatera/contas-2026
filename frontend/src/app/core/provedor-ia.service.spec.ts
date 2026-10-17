import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProvedorIaRequest, ProvedorIaService } from './provedor-ia.service';

describe('ProvedorIaService', () => {
  let service: ProvedorIaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProvedorIaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const provedor = {
    id: 'uuid-1',
    nome: 'OpenAI',
    tipo: 'OPENAI' as const,
    modelo: 'gpt-4o-mini',
    baseUrl: null,
    possuiApiKey: true,
    apiKeyMascarada: 'sk-...abcd',
    temperatura: 0.5,
    maxTokens: 512,
    timeoutSegundos: 60,
    padrao: true,
    ativo: true,
    criadoEm: '2026-10-17T00:00:00Z',
  };
  const request: ProvedorIaRequest = {
    nome: 'OpenAI',
    tipo: 'OPENAI',
    modelo: 'gpt-4o-mini',
    baseUrl: null,
    apiKey: 'sk-proj-abcd',
    temperatura: 0.5,
    maxTokens: 512,
    timeoutSegundos: 60,
    padrao: true,
    ativo: true,
  };

  it('lists providers from the response envelope', () => {
    service.listar().subscribe((result) => expect(result).toEqual([provedor]));
    http.expectOne('/api/ia/provedores').flush({ data: [provedor] });
  });

  it('loads a provider by ID', () => {
    service.buscarPorId('uuid-1').subscribe((result) => expect(result).toEqual(provedor));
    http.expectOne('/api/ia/provedores/uuid-1').flush({ data: provedor });
  });

  it('creates a provider', () => {
    service.criar(request).subscribe((result) => expect(result).toEqual(provedor));
    const req = http.expectOne('/api/ia/provedores');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: provedor });
  });

  it('updates a provider', () => {
    service.atualizar('uuid-1', request).subscribe((result) => expect(result).toEqual(provedor));
    const req = http.expectOne('/api/ia/provedores/uuid-1');
    expect(req.request.method).toBe('PUT');
    req.flush({ data: provedor });
  });

  it('deletes a provider', () => {
    service.excluir('uuid-1').subscribe();
    const req = http.expectOne('/api/ia/provedores/uuid-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('tests a provider with the given prompt', () => {
    const teste = {
      sucesso: true,
      prompt: 'Olá',
      resposta: 'Oi!',
      modelo: 'gpt-4o-mini',
      latenciaMs: 820,
      tokensEntrada: 5,
      tokensSaida: 2,
      erro: null,
    };
    service.testar('uuid-1', 'Olá').subscribe((result) => expect(result).toEqual(teste));
    const req = http.expectOne('/api/ia/provedores/uuid-1/testar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ prompt: 'Olá' });
    req.flush({ data: teste });
  });
});
