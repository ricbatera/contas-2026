import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SaidaRequest, SaidaService } from './saida.service';

describe('SaidaService', () => {
  let service: SaidaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SaidaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const saida = {
    id: 'expense-1',
    descricao: 'Mercado',
    descricaoFatura: 'Compra',
    contaFixa: true,
    valorPorParcela: 100,
    valorTotal: 200,
    quantidadeParcelas: 2,
    formaPagamento: 'CARTAO_CREDITO' as const,
    modoResponsabilidade: 'USUARIO' as const,
    dataInicial: null,
    primeiroVencimento: '2026-11-15',
    cartaoId: 'card-1',
    cartaoNome: 'Cartão',
    categoriaId: 'category-1',
    categoriaNome: 'Alimentação',
    subcategoriaId: 'subcategory-1',
    subcategoriaNome: 'Mercado',
    responsavelPessoaId: null,
    responsabilidades: [],
    parcelas: [],
    criadoEm: null,
  };
  const request: SaidaRequest = {
    descricao: 'Mercado',
    descricaoFatura: 'Compra',
    contaFixa: true,
    valorPorParcela: 100,
    quantidadeParcelas: 2,
    formaPagamento: 'CARTAO_CREDITO',
    dataInicial: null,
    primeiroVencimento: '2026-11-15',
    cartaoId: 'card-1',
    categoriaId: 'category-1',
    subcategoriaId: 'subcategory-1',
    modoResponsabilidade: 'USUARIO',
    responsavelPessoaId: null,
    partes: [],
  };

  it('lists expenses from the API response envelope', () => {
    service.listar().subscribe((result) => expect(result).toEqual([saida]));
    http.expectOne('/api/saidas').flush({ data: [saida] });
  });

  it('loads an expense by ID', () => {
    service.buscarPorId('expense-1').subscribe((result) => expect(result).toEqual(saida));
    http.expectOne('/api/saidas/expense-1').flush({ data: saida });
  });

  it('creates an expense', () => {
    service.criar(request).subscribe((result) => expect(result).toEqual(saida));
    const req = http.expectOne('/api/saidas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: saida });
  });

  it('updates an expense', () => {
    service.atualizar('expense-1', request).subscribe((result) => expect(result).toEqual(saida));
    const req = http.expectOne('/api/saidas/expense-1');
    expect(req.request.method).toBe('PUT');
    req.flush({ data: saida });
  });

  it('deletes an expense', () => {
    service.excluir('expense-1').subscribe();
    const req = http.expectOne('/api/saidas/expense-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
