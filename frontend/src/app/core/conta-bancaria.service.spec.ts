import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ContaBancariaRequest, ContaBancariaService } from './conta-bancaria.service';

describe('ContaBancariaService', () => {
  let service: ContaBancariaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ContaBancariaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const conta = {
    id: 'uuid-1',
    nome: 'Conta principal',
    banco: 'Banco',
    tipoConta: 'CORRENTE' as const,
    agencia: '0001',
    numeroConta: '12345-6',
    saldoInicial: 1250.5,
    cor: '#1E6FD0',
    ativo: true,
    criadoEm: '2026-01-01T00:00:00Z',
  };
  const request: ContaBancariaRequest = {
    nome: conta.nome,
    banco: conta.banco,
    tipoConta: conta.tipoConta,
    agencia: conta.agencia,
    numeroConta: conta.numeroConta,
    saldoInicial: conta.saldoInicial,
    cor: conta.cor,
    ativo: conta.ativo,
  };

  it('lists accounts from the response envelope', () => {
    service.listar().subscribe((result) => expect(result).toEqual([conta]));
    http.expectOne('/api/contas-bancarias').flush({ data: [conta] });
  });

  it('loads an account by ID', () => {
    service.buscarPorId('uuid-1').subscribe((result) => expect(result).toEqual(conta));
    http.expectOne('/api/contas-bancarias/uuid-1').flush({ data: conta });
  });

  it('creates an account using the API request fields', () => {
    service.criar(request).subscribe((result) => expect(result).toEqual(conta));
    const req = http.expectOne('/api/contas-bancarias');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: conta });
  });

  it('updates an account by ID', () => {
    service.atualizar('uuid-1', request).subscribe((result) => expect(result).toEqual(conta));
    const req = http.expectOne('/api/contas-bancarias/uuid-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ data: conta });
  });

  it('deletes an account without expecting a response envelope', () => {
    service.excluir('uuid-1').subscribe();
    const req = http.expectOne('/api/contas-bancarias/uuid-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
