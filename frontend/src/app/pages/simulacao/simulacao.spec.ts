import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Simulacao } from './simulacao';

describe('Simulacao', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Simulacao],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function flushProjection(): void {
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
        gastosLancados: 80,
        bucketsReservados: 15,
        contasFixasMesAnterior: 5,
        totalPrevisto: 100,
      };
    }) });
  }

  it('adds a 60-installment simulation and updates the projected monthly totals', () => {
    const fixture = TestBed.createComponent(Simulacao);
    fixture.detectChanges();
    flushProjection();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = 'Notebook';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    const amount = element.querySelector<HTMLInputElement>('[formControlName="valorPorParcela"]')!;
    amount.value = '850.1';
    amount.dispatchEvent(new Event('input', { bubbles: true }));
    const installments = element.querySelector<HTMLSelectElement>('[formControlName="quantidadeParcelas"]')!;
    expect(installments.options).toHaveLength(60);
    installments.value = '60';
    installments.dispatchEvent(new Event('change', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();

    const secondName = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    secondName.value = 'Mouse';
    secondName.dispatchEvent(new Event('input', { bubbles: true }));
    const secondAmount = element.querySelector<HTMLInputElement>('[formControlName="valorPorParcela"]')!;
    secondAmount.value = '0.2';
    secondAmount.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();

    expect(element.querySelector('.simulation-item-description')?.textContent).toContain('Notebook');
    expect(element.querySelectorAll('.simulation-item')).toHaveLength(2);
    expect(element.querySelector('.projection-month .month-breakdown')?.textContent).toContain('850,30');
    expect(element.querySelector('.projection-month .month-totals')?.textContent).toContain('950,30');
    expect(element.querySelectorAll('.projection-month')).toHaveLength(12);
    expect(JSON.parse(localStorage.getItem('simulacao-orcamento-v1') ?? '[]')).toEqual([
      expect.objectContaining({
        nome: 'Notebook',
        valorPorParcela: 850.1,
        quantidadeParcelas: 60,
      }),
      expect.objectContaining({
        nome: 'Mouse',
        valorPorParcela: 0.2,
        quantidadeParcelas: 1,
      }),
    ]);
  });

  it('restores locally saved simulations and stops adding their installments after the final month', () => {
    localStorage.setItem('simulacao-orcamento-v1', JSON.stringify([{
      id: 'saved-1',
      nome: 'Sofá',
      valorPorParcela: 200,
      quantidadeParcelas: 2,
    }]));

    const fixture = TestBed.createComponent(Simulacao);
    fixture.detectChanges();
    flushProjection();
    fixture.detectChanges();

    const months = (fixture.nativeElement as HTMLElement).querySelectorAll('.projection-month');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sofá');
    expect(months[0].textContent).toContain('200,00');
    expect(months[1].textContent).toContain('200,00');
    expect(months[2].textContent).toContain('Sem novas parcelas');

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[aria-label="Remover Sofá"]')!.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Sofá');
    expect(JSON.parse(localStorage.getItem('simulacao-orcamento-v1') ?? '[]')).toEqual([]);
  });

  it('updates the forecast when the displayed month range changes', () => {
    const fixture = TestBed.createComponent(Simulacao);
    fixture.detectChanges();
    const now = new Date();
    const initialRequest = http.expectOne((request) =>
      request.url === '/api/dashboard/projecao'
      && request.params.get('quantidade') === '12',
    );
    const selector = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLSelectElement>('.simulation-period select')!;
    selector.value = '3';
    selector.dispatchEvent(new Event('change', { bubbles: true }));
    initialRequest.flush({ data: [] });
    http.expectOne((request) =>
      request.url === '/api/dashboard/projecao'
      && request.params.get('mes') === String(now.getMonth() + 1)
      && request.params.get('ano') === String(now.getFullYear())
      && request.params.get('quantidade') === '3',
    ).flush({ data: Array.from({ length: 3 }, (_, index) => {
      const month = new Date(Date.UTC(now.getFullYear(), now.getMonth() + index, 1));
      return {
        mes: month.getUTCMonth() + 1,
        ano: month.getUTCFullYear(),
        gastosLancados: 0,
        bucketsReservados: 0,
        contasFixasMesAnterior: 0,
        totalPrevisto: 0,
      };
    }) });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.projection-month')).toHaveLength(3);
  });

  it('reports malformed local simulation data instead of silently replacing it', () => {
    localStorage.setItem('simulacao-orcamento-v1', '{invalid');
    const fixture = TestBed.createComponent(Simulacao);
    fixture.detectChanges();
    flushProjection();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent)
      .toContain('As simulações salvas estão inválidas');
    expect(localStorage.getItem('simulacao-orcamento-v1')).toBe('{invalid');
  });

  it('requires explicit confirmation before discarding malformed local data', () => {
    localStorage.setItem('simulacao-orcamento-v1', '{invalid');
    const fixture = TestBed.createComponent(Simulacao);
    fixture.detectChanges();
    flushProjection();
    fixture.detectChanges();

    const reset = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('Descartar dados inválidos'))!;
    expect(reset.textContent).toContain('Descartar dados inválidos');
    reset.click();
    fixture.detectChanges();

    expect(localStorage.getItem('simulacao-orcamento-v1')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('As simulações salvas estão inválidas');
  });
});
