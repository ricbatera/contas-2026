import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CartaoRequest, CartaoService } from './cartao.service';

describe('CartaoService', () => {
  let service: CartaoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CartaoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const cartao = {
    id: 'uuid-1',
    nome: 'Principal',
    banco: 'Banco',
    bandeira: 'VISA' as const,
    ultimosDigitos: '1234',
    cor: '#1E6FD0',
    criadoEm: '2026-01-01T00:00:00Z',
    tipo: 'PRINCIPAL' as const,
    virtual: false,
    cartaoPrincipalId: null,
    ativo: true,
  };
  const request: CartaoRequest = {
    nome: cartao.nome,
    banco: cartao.banco,
    bandeira: cartao.bandeira,
    ultimosDigitos: cartao.ultimosDigitos,
    cor: cartao.cor,
    tipo: 'PRINCIPAL',
    virtual: false,
    cartaoPrincipalId: null,
    ativo: true,
  };

  it('lists cards from the API response envelope', () => {
    service.listar().subscribe((result) => expect(result).toEqual([cartao]));
    http.expectOne('/api/cartoes').flush({ data: [cartao] });
  });

  it('loads a card by ID', () => {
    service.buscarPorId('uuid-1').subscribe((result) => expect(result).toEqual(cartao));
    http.expectOne('/api/cartoes/uuid-1').flush({ data: cartao });
  });

  it('lists principal cards for additional-card association', () => {
    service.listarPrincipais().subscribe((result) => expect(result).toEqual([cartao]));
    http.expectOne('/api/cartoes/principais').flush({ data: [cartao] });
  });

  it('creates a card with the API request fields', () => {
    service.criar(request).subscribe((result) => expect(result).toEqual(cartao));
    const req = http.expectOne('/api/cartoes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: cartao });
  });

  it('updates a card by ID', () => {
    service.atualizar('uuid-1', request).subscribe((result) => expect(result).toEqual(cartao));
    const req = http.expectOne('/api/cartoes/uuid-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ data: cartao });
  });
});
