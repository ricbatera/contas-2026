import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let http: HttpTestingController;

  function responderProjecao(mes: number, ano: number, quantidade = 12): void {
    const request = http.expectOne((item) =>
      item.url === '/api/dashboard/projecao'
      && item.params.get('mes') === String(mes)
      && item.params.get('ano') === String(ano)
      && item.params.get('quantidade') === String(quantidade),
    );
    const data = Array.from({ length: quantidade }, (_, index) => {
      const competencia = new Date(Date.UTC(ano, mes - 1 + index, 1));
      return {
        mes: competencia.getUTCMonth() + 1,
        ano: competencia.getUTCFullYear(),
        gastosLancados: 125.5,
        bucketsReservados: 200,
        contasFixasMesAnterior: 25,
        totalPrevisto: 350.5,
        receitasPrevistas: 0,
        receitasRecebidas: 0,
        saldoPrevisto: -350.5,
      };
    });
    request.flush({ data });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('defaults to the current competence and displays forecast breakdown', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const current = new Date();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector<HTMLSelectElement>('.dashboard-period select')?.value)
      .toBe(String(current.getMonth() + 1));
    expect(element.querySelector<HTMLInputElement>('.dashboard-period input')?.value)
      .toBe(String(current.getFullYear()));
    const request = http.expectOne((item) =>
      item.url === '/api/dashboard'
      && item.params.get('mes') === String(current.getMonth() + 1)
      && item.params.get('ano') === String(current.getFullYear()),
    );
    request.flush({ data: {
      mes: current.getMonth() + 1,
      ano: current.getFullYear(),
      gastosLancados: 125.5,
      bucketsReservados: 200,
      contasFixasMesAnterior: 25,
      totalPrevisto: 350.5,
      receitasPrevistas: 0,
      receitasRecebidas: 0,
      saldoPrevisto: -350.5,
    } });
    responderProjecao(current.getMonth() + 1, current.getFullYear());
    fixture.detectChanges();

    expect(element.textContent).toContain('Saídas lançadas no mês');
    expect(element.textContent).toContain('Reservas dos buckets');
    expect(element.textContent).toContain('Contas fixas do mês anterior');
    expect(element.textContent).toContain('R$ 350,50');
    const projectionCount = element.querySelector<HTMLSelectElement>('.projection-count select')!;
    expect(projectionCount.value).toBe('12');
    expect(projectionCount.options).toHaveLength(22);
    expect(projectionCount.options[0]?.value).toBe('3');
    expect(projectionCount.options[21]?.value).toBe('24');
    expect(element.querySelectorAll('.chart-bar')).toHaveLength(12);
    expect(element.querySelector('.chart-total')?.textContent).toContain('350,5');
    expect(element.textContent).toContain('Saídas lançadas');
  });

  it('consults the selected competence and reports loading failures', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne('/api/dashboard?mes=' + (new Date().getMonth() + 1) + '&ano=' + new Date().getFullYear())
      .flush({ data: {
        mes: new Date().getMonth() + 1,
        ano: new Date().getFullYear(),
        gastosLancados: 0,
        bucketsReservados: 0,
        contasFixasMesAnterior: 0,
        totalPrevisto: 0,
        receitasPrevistas: 0,
        receitasRecebidas: 0,
        saldoPrevisto: -0,
      } });
    responderProjecao(new Date().getMonth() + 1, new Date().getFullYear());
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLSelectElement>('.dashboard-period select')!.value = '12';
    element.querySelector<HTMLSelectElement>('.dashboard-period select')!
      .dispatchEvent(new Event('change', { bubbles: true }));
    element.querySelector<HTMLInputElement>('.dashboard-period input')!.value = '2025';
    element.querySelector<HTMLInputElement>('.dashboard-period input')!
      .dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLButtonElement>('.dashboard-period button')!.click();
    fixture.detectChanges();

    const request = http.expectOne((item) =>
      item.url === '/api/dashboard'
      && item.params.get('mes') === '12'
      && item.params.get('ano') === '2025',
    );
    const projection = http.expectOne((item) =>
      item.url === '/api/dashboard/projecao'
      && item.params.get('mes') === '12'
      && item.params.get('ano') === '2025'
      && item.params.get('quantidade') === '12',
    );
    projection.flush({ data: Array.from({ length: 12 }, (_, index) => ({
      mes: ((index + 11) % 12) + 1,
      ano: 2025 + Math.floor((index + 11) / 12),
      gastosLancados: 0,
      bucketsReservados: 0,
      contasFixasMesAnterior: 0,
      totalPrevisto: 0,
      receitasPrevistas: 0,
      receitasRecebidas: 0,
      saldoPrevisto: -0,
    })) });
    request.flush({ message: 'Unavailable' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(element.textContent).toContain('Unavailable');
  });

  it('reloads the chart when its displayed month count changes', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const now = new Date();
    http.expectOne((item) => item.url === '/api/dashboard').flush({ data: {
      mes: now.getMonth() + 1,
      ano: now.getFullYear(),
      gastosLancados: 0,
      bucketsReservados: 0,
      contasFixasMesAnterior: 0,
      totalPrevisto: 0,
      receitasPrevistas: 0,
      receitasRecebidas: 0,
      saldoPrevisto: -0,
    } });
    responderProjecao(now.getMonth() + 1, now.getFullYear());
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const select = element.querySelector<HTMLSelectElement>('.projection-count select')!;
    select.value = '3';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    responderProjecao(now.getMonth() + 1, now.getFullYear(), 3);
    fixture.detectChanges();

    expect(select.value).toBe('3');
    expect(element.querySelectorAll('.chart-bar')).toHaveLength(3);
  });

  it('shows chart loading errors and allows retry', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const now = new Date();
    http.expectOne((item) => item.url === '/api/dashboard').flush({ data: {
      mes: now.getMonth() + 1,
      ano: now.getFullYear(),
      gastosLancados: 0,
      bucketsReservados: 0,
      contasFixasMesAnterior: 0,
      totalPrevisto: 0,
      receitasPrevistas: 0,
      receitasRecebidas: 0,
      saldoPrevisto: -0,
    } });
    http.expectOne((item) => item.url === '/api/dashboard/projecao')
      .flush({ message: 'Falha na projeção' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Falha na projeção');
    element.querySelector<HTMLButtonElement>('.projection-error button')!.click();
    responderProjecao(now.getMonth() + 1, now.getFullYear());
    fixture.detectChanges();
    expect(element.querySelectorAll('.chart-bar')).toHaveLength(12);
  });

  it('loads expense details only when clicked and closes the modal with Escape', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const now = new Date();
    http.expectOne((item) => item.url === '/api/dashboard').flush({ data: {
      mes: now.getMonth() + 1,
      ano: now.getFullYear(),
      gastosLancados: 125.5,
      bucketsReservados: 200,
      contasFixasMesAnterior: 25,
      totalPrevisto: 350.5,
      receitasPrevistas: 0,
      receitasRecebidas: 0,
      saldoPrevisto: -350.5,
    } });
    responderProjecao(now.getMonth() + 1, now.getFullYear());
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const items = element.querySelectorAll<HTMLButtonElement>('.forecast-breakdown .forecast-breakdown-item');
    expect(items.length).toBe(3);
    items[0]!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Carregando itens...');
    const request = http.expectOne((item) =>
      item.url === '/api/dashboard/saidas'
      && item.params.get('mes') === String(now.getMonth() + 1)
      && item.params.get('ano') === String(now.getFullYear()),
    );
    request.flush({ data: [{
      descricao: 'Mercado',
      categoriaNome: 'Casa',
      subcategoriaNome: 'Alimentação',
      vencimento: '2026-10-15',
      numeroParcela: 1,
      quantidadeParcelas: 1,
      valor: 125.5,
    }] });
    fixture.detectChanges();

    expect(element.querySelector('[role="dialog"]')).not.toBeNull();
    expect(element.textContent).toContain('Mercado');
    expect(element.textContent).toContain('Casa · Alimentação');
    expect(element.textContent).toContain('Parcela 1/1');
    expect(element.textContent).toContain('125,50');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
  });

  it('shows forecast income, balance and income details', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const now = new Date();
    http.expectOne((item) => item.url === '/api/dashboard').flush({ data: {
      mes: now.getMonth() + 1,
      ano: now.getFullYear(),
      gastosLancados: 1000,
      bucketsReservados: 0,
      contasFixasMesAnterior: 0,
      totalPrevisto: 1000,
      receitasPrevistas: 5000,
      receitasRecebidas: 4800,
      saldoPrevisto: 4000,
    } });
    responderProjecao(now.getMonth() + 1, now.getFullYear());
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Receitas previstas no mês');
    expect(element.textContent).toMatch(/R\$\s5\.000,00/);
    expect(element.textContent).toMatch(/Recebido até agora: R\$\s4\.800,00/);
    expect(element.textContent).toMatch(/Saldo previsto\s*R\$\s4\.000,00/);
    element.querySelector<HTMLButtonElement>('.forecast-balance .forecast-breakdown-item')!.click();
    http.expectOne((item) => item.url === '/api/dashboard/entradas').flush({ data: [{
      descricao: 'Salário',
      categoriaNome: 'Salário',
      subcategoriaNome: 'Salário mensal',
      vencimento: '2026-10-05',
      numeroParcela: null,
      quantidadeParcelas: null,
      valor: 5000,
    }] });
    fixture.detectChanges();
    expect(element.textContent).toContain('Receitas previstas no mês');
    expect(element.textContent).toContain('Salário · Salário mensal');
  });

  it('loads bucket and previous-month fixed expense details on demand', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne((item) => item.url === '/api/dashboard').flush({ data: {
      mes: 10,
      ano: 2026,
      gastosLancados: 0,
      bucketsReservados: 200,
      contasFixasMesAnterior: 50,
      totalPrevisto: 250,
      receitasPrevistas: 0,
      receitasRecebidas: 0,
      saldoPrevisto: -250,
    } });
    responderProjecao(10, 2026);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const buttons = element.querySelectorAll<HTMLButtonElement>('.forecast-breakdown .forecast-breakdown-item');

    buttons[1]!.click();
    fixture.detectChanges();
    http.expectOne('/api/dashboard/buckets').flush({ data: [{
      descricao: null,
      categoriaNome: 'Saúde',
      subcategoriaNome: 'Medicamentos',
      vencimento: null,
      numeroParcela: null,
      quantidadeParcelas: null,
      valor: 200,
    }] });
    fixture.detectChanges();
    expect(element.textContent).toContain('Saúde · Medicamentos');
    expect(element.textContent).toContain('200,00');
    element.querySelector<HTMLButtonElement>('.dashboard-modal-close')!.click();
    fixture.detectChanges();

    buttons[2]!.click();
    fixture.detectChanges();
    const request = http.expectOne((item) =>
      item.url === '/api/dashboard/contas-fixas'
      && item.params.get('mes') === '10'
      && item.params.get('ano') === '2026',
    );
    request.flush({ data: [{
      descricao: 'Internet',
      categoriaNome: 'Casa',
      subcategoriaNome: 'Internet',
      vencimento: '2026-09-10',
      numeroParcela: 1,
      quantidadeParcelas: 1,
      valor: 50,
    }] });
    fixture.detectChanges();
    expect(element.textContent).toContain('Internet');
    expect(element.textContent).toContain('10/09/2026');
    expect(element.textContent).toContain('50,00');
  });

  it('shows an error when loading a breakdown fails', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const now = new Date();
    http.expectOne((item) => item.url === '/api/dashboard').flush({ data: {
      mes: now.getMonth() + 1,
      ano: now.getFullYear(),
      gastosLancados: 1,
      bucketsReservados: 0,
      contasFixasMesAnterior: 0,
      totalPrevisto: 1,
      receitasPrevistas: 0,
      receitasRecebidas: 0,
      saldoPrevisto: -1,
    } });
    responderProjecao(now.getMonth() + 1, now.getFullYear());
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.forecast-breakdown-item')!.click();
    fixture.detectChanges();
    http.expectOne((item) => item.url === '/api/dashboard/saidas')
      .flush({ message: 'Falha ao consultar detalhes' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Falha ao consultar detalhes');
  });
});
