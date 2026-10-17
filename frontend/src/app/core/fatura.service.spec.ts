import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Fatura, FaturaCompetencia, FaturaService } from './fatura.service';

describe('FaturaService', () => {
  let service: FaturaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FaturaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists available statement periods for a primary card', () => {
    const periods: FaturaCompetencia[] = [
      { competencia: 202611, vencimento: '2026-11-15', total: 100 },
    ];

    service.listarCompetencias('cartao / 1').subscribe((result) => expect(result).toEqual(periods));

    http.expectOne('/api/faturas/cartoes/cartao%20%2F%201/competencias').flush({ data: periods });
  });

  it('loads a statement by card and competence', () => {
    const statement: Fatura = {
      cartaoId: 'card-1',
      cartaoNome: 'Principal',
      banco: 'Banco',
      competencia: 202611,
      vencimento: '2026-11-15',
      total: 100,
      parcelas: [],
    };

    service.buscar('card-1', 202611).subscribe((result) => expect(result).toEqual(statement));

    http.expectOne('/api/faturas/cartoes/card-1/202611').flush({ data: statement });
  });
});
