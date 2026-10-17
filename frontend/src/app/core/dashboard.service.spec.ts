import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let http: HttpTestingController;
  let service: DashboardService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(DashboardService);
  });

  afterEach(() => http.verify());

  it('loads the current forecast for the requested month and year', () => {
    let result: unknown;
    service.consultar(10, 2026).subscribe((response) => result = response);
    const request = http.expectOne((item) =>
      item.url === '/api/dashboard'
      && item.params.get('mes') === '10'
      && item.params.get('ano') === '2026',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ data: {
      mes: 10,
      ano: 2026,
      gastosLancados: 125.5,
      bucketsReservados: 200,
      contasFixasMesAnterior: 25,
      totalPrevisto: 350.5,
    } });
    expect(result).toEqual({
      mes: 10,
      ano: 2026,
      gastosLancados: 125.5,
      bucketsReservados: 200,
      contasFixasMesAnterior: 25,
      totalPrevisto: 350.5,
    });
  });

  it('loads a stacked monthly projection with the requested month count', () => {
    let result: unknown;
    service.consultarProjecao(10, 2026, 12).subscribe((response) => result = response);
    const request = http.expectOne((item) =>
      item.url === '/api/dashboard/projecao'
      && item.params.get('mes') === '10'
      && item.params.get('ano') === '2026'
      && item.params.get('quantidade') === '12',
    );
    expect(request.request.method).toBe('GET');
    const months = [{
      mes: 10,
      ano: 2026,
      gastosLancados: 125.5,
      bucketsReservados: 200,
      contasFixasMesAnterior: 25,
      totalPrevisto: 350.5,
    }];
    request.flush({ data: months });
    expect(result).toEqual(months);
  });

  it('loads each detail collection from its on-demand endpoint', () => {
    service.listarSaidas(10, 2026).subscribe();
    const expenses = http.expectOne((item) =>
      item.url === '/api/dashboard/saidas'
      && item.params.get('mes') === '10'
      && item.params.get('ano') === '2026',
    );
    expenses.flush({ data: [] });

    service.listarBuckets().subscribe();
    http.expectOne('/api/dashboard/buckets').flush({ data: [] });

    service.listarContasFixas(10, 2026).subscribe();
    const fixed = http.expectOne((item) =>
      item.url === '/api/dashboard/contas-fixas'
      && item.params.get('mes') === '10'
      && item.params.get('ano') === '2026',
    );
    fixed.flush({ data: [] });
  });
});
