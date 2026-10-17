import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PreSaidaRequest, PreSaidaService } from './pre-saida.service';

describe('PreSaidaService', () => {
  let service: PreSaidaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PreSaidaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('filters pre-expenses by status and import', () => {
    service.listar({ status: 'PENDENTE', importacaoId: 'imp-1' }).subscribe((result) => expect(result).toEqual([]));

    http.expectOne('/api/pre-saidas?status=PENDENTE&importacaoId=imp-1').flush({ data: [] });
  });

  it('updates a pre-expense before approval', () => {
    const request: PreSaidaRequest = {
      descricao: 'Loja',
      cartaoId: 'card-1',
      categoriaId: 'cat-1',
      subcategoriaId: 'sub-1',
      valorPorParcela: 100,
      numeroParcela: 3,
      quantidadeParcelas: 10,
    };

    service.atualizar('pre-1', request).subscribe((result) => expect(result.id).toBe('pre-1'));

    const req = http.expectOne('/api/pre-saidas/pre-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ data: { id: 'pre-1' } });
  });

  it('approves, rejects and reopens pre-expenses', () => {
    for (const acao of ['aprovar', 'rejeitar', 'reabrir'] as const) {
      service[acao]('pre-1').subscribe();
      const req = http.expectOne(`/api/pre-saidas/pre-1/${acao}`);
      expect(req.request.method).toBe('POST');
      req.flush({ data: { id: 'pre-1' } });
    }
  });

  it('approves a batch of pre-expenses', () => {
    service.aprovarLote(['a', 'b']).subscribe((result) => expect(result.falhas).toEqual([]));

    const req = http.expectOne('/api/pre-saidas/aprovar-lote');
    expect(req.request.body).toEqual({ ids: ['a', 'b'] });
    req.flush({ data: { aprovadas: [], falhas: [] } });
  });
});
