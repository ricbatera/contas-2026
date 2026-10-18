import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EntradaRequest, EntradaService } from './entrada.service';

describe('EntradaService', () => {
  let service: EntradaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EntradaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const request: EntradaRequest = {
    descricao: 'Salário',
    valor: 5000,
    recorrente: true,
    dataInicial: '2026-10-05',
    dataFinal: null,
    categoriaId: 'category-1',
    subcategoriaId: 'subcategory-1',
    contaBancariaId: null,
  };

  it('creates and updates income entries', () => {
    service.criar(request).subscribe((entrada) => expect(entrada.id).toBe('income-1'));
    const criar = http.expectOne('/api/entradas');
    expect(criar.request.method).toBe('POST');
    expect(criar.request.body).toEqual(request);
    criar.flush({ data: { id: 'income-1' } });

    service.atualizar('income-1', request).subscribe();
    const atualizar = http.expectOne('/api/entradas/income-1');
    expect(atualizar.request.method).toBe('PUT');
    atualizar.flush({ data: { id: 'income-1' } });
  });

  it('lists the occurrences of a month', () => {
    service.listarOcorrencias(10, 2026).subscribe((ocorrencias) => expect(ocorrencias).toHaveLength(1));
    const listar = http.expectOne((req) => req.url === '/api/entradas/ocorrencias');
    expect(listar.request.params.get('mes')).toBe('10');
    expect(listar.request.params.get('ano')).toBe('2026');
    listar.flush({ data: [{ id: 'occurrence-1' }] });
  });

  it('receives, cancels and reopens an occurrence', () => {
    service.receber('occurrence-1', { valorRecebido: 4900, dataRecebimento: '2026-10-05' }).subscribe();
    const receber = http.expectOne('/api/entradas/ocorrencias/occurrence-1/receber');
    expect(receber.request.method).toBe('POST');
    expect(receber.request.body).toEqual({ valorRecebido: 4900, dataRecebimento: '2026-10-05' });
    receber.flush({ data: { id: 'occurrence-1', status: 'RECEBIDA' } });

    service.cancelar('occurrence-1').subscribe();
    http.expectOne('/api/entradas/ocorrencias/occurrence-1/cancelar').flush({ data: { id: 'occurrence-1' } });
    service.reabrir('occurrence-1').subscribe();
    http.expectOne('/api/entradas/ocorrencias/occurrence-1/reabrir').flush({ data: { id: 'occurrence-1' } });
  });
});
