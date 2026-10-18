import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Application routes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('accessToken', 'test-access-token');
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // O shell inicia o monitor de importações de fatura, que consulta as que estão processando.
    http.match('/api/faturas/importacoes').forEach((request) => request.flush({ data: [] }));
    http.verify();
    localStorage.clear();
  });

  it('renders the monthly forecast dashboard inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/dashboard');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    const now = new Date();
    const request = http.expectOne((item) =>
      item.url === '/api/dashboard'
      && item.params.get('mes') === String(now.getMonth() + 1)
      && item.params.get('ano') === String(now.getFullYear()),
    );
    request.flush({ data: {
      mes: now.getMonth() + 1,
      ano: now.getFullYear(),
      gastosLancados: 100,
      bucketsReservados: 200,
      contasFixasMesAnterior: 50,
      totalPrevisto: 350,
    } });
    http.expectOne((item) => item.url === '/api/dashboard/projecao')
      .flush({ data: Array.from({ length: 12 }, (_, index) => {
        const competencia = new Date(Date.UTC(now.getFullYear(), now.getMonth() + index, 1));
        return {
          mes: competencia.getUTCMonth() + 1,
          ano: competencia.getUTCFullYear(),
          gastosLancados: 0,
          bucketsReservados: 0,
          contasFixasMesAnterior: 0,
          totalPrevisto: 0,
        };
      }) });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('PREVISÃO DE GASTOS');
    expect(harness.routeNativeElement?.textContent).toContain('R$');
    expect(TestBed.inject(Router).url).toBe('/home/dashboard');
  });

  it('renders the simulation screen inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/simulacao');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    const now = new Date();
    http.expectOne((request) =>
      request.url === '/api/dashboard/projecao'
      && request.params.get('mes') === String(now.getMonth() + 1)
      && request.params.get('ano') === String(now.getFullYear())
      && request.params.get('quantidade') === '12',
    ).flush({ data: Array.from({ length: 12 }, (_, index) => {
      const month = new Date(Date.UTC(now.getFullYear(), now.getMonth() + index, 1));
      return {
        mes: month.getUTCMonth() + 1,
        ano: month.getUTCFullYear(),
        gastosLancados: 100,
        bucketsReservados: 50,
        contasFixasMesAnterior: 20,
        totalPrevisto: 170,
      };
    }) });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Impacto no orçamento');
    expect(harness.routeNativeElement?.textContent).toContain('Despesas simuladas');
    expect(TestBed.inject(Router).url).toBe('/home/simulacao');
  });

  it('renders the cards list inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/cartoes');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    http.expectOne('/api/cartoes').flush({ data: [] });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Seus cartões');
    expect(TestBed.inject(Router).url).toBe('/home/cartoes');
  });

  it('renders the accounts list inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/contas');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    http.expectOne('/api/contas-bancarias').flush({ data: [] });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Contas bancárias');
    expect(TestBed.inject(Router).url).toBe('/home/contas');
  });

  it('renders the people list inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/pessoas');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    http.expectOne('/api/pessoas').flush({ data: [] });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Pessoas');
    expect(harness.routeNativeElement?.textContent).toContain('Nenhuma pessoa cadastrada');
    expect(TestBed.inject(Router).url).toBe('/home/pessoas');
  });

  it('renders the expense list inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/saidas');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    http.expectOne('/api/saidas').flush({ data: [] });
    http.expectOne('/api/cartoes').flush({ data: [] });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Nenhuma saída cadastrada');
    expect(TestBed.inject(Router).url).toBe('/home/saidas');
  });

  it('renders invoice consultation inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/faturas');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    http.expectOne('/api/cartoes/principais').flush({
      data: [{
        id: 'card-1',
        nome: 'Principal',
        banco: 'Banco',
        bandeira: 'VISA',
        ultimosDigitos: '1234',
        cor: '#123456',
        criadoEm: '2026-10-09T00:00:00',
        tipo: 'PRINCIPAL',
        virtual: false,
        cartaoPrincipalId: null,
        ativo: true,
      }],
    });
    http.expectOne('/api/faturas/cartoes/card-1/competencias').flush({
      data: [{ competencia: 202611, vencimento: '2026-11-15', total: 50 }],
    });
    http.expectOne('/api/faturas/cartoes/card-1/202611').flush({
      data: {
        cartaoId: 'card-1',
        cartaoNome: 'Principal',
        banco: 'Banco',
        competencia: 202611,
        vencimento: '2026-11-15',
        total: 50,
        parcelas: [],
      },
    });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Faturas');
    expect(harness.routeNativeElement?.textContent).toContain('Principal');
    expect(harness.routeNativeElement?.textContent).toContain('R$');
    expect(TestBed.inject(Router).url).toBe('/home/faturas');
  });

  it('renders bucket monthly overview inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/buckets');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    const now = new Date();
    const request = http.expectOne((item) =>
      item.url === '/api/buckets'
      && item.params.get('mes') === String(now.getMonth() + 1)
      && item.params.get('ano') === String(now.getFullYear()),
    );
    request.flush({ data: [] });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Buckets');
    expect(harness.routeNativeElement?.textContent).toContain('Nenhum bucket cadastrado');
    expect(TestBed.inject(Router).url).toBe('/home/buckets');
  });

  it('renders categories inside the authenticated home shell', async () => {
    const harness = await RouterTestingHarness.create('/home/categorias');
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    http.expectOne('/api/categorias').flush({ data: [] });
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Categorias');
    expect(harness.routeNativeElement?.textContent).toContain('Nenhuma categoria de despesas');
    expect(TestBed.inject(Router).url).toBe('/home/categorias');
  });
});
