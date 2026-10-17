import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BucketsList } from './buckets-list';

describe('BucketsList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BucketsList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the current competence and displays planned, consumed and remaining values', () => {
    const fixture = TestBed.createComponent(BucketsList);
    fixture.detectChanges();
    const now = new Date();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector<HTMLSelectElement>('.bucket-period select')?.value)
      .toBe(String(now.getMonth() + 1));
    expect(element.querySelector<HTMLInputElement>('.bucket-period input')?.value)
      .toBe(String(now.getFullYear()));
    const request = http.expectOne((item) =>
      item.url === '/api/buckets'
      && item.params.get('mes') === String(now.getMonth() + 1)
      && item.params.get('ano') === String(now.getFullYear()),
    );
    request.flush({ data: [{
      id: 'bucket-1',
      categoriaId: 'health',
      categoriaNome: 'Saúde',
      subcategoriaId: 'medicine',
      subcategoriaNome: 'Medicamentos',
      responsavelPessoaId: null,
      responsavelNome: 'Ana Silva',
      responsavelUsuario: true,
      valorMensal: 200,
      valorConsumido: 75,
      saldo: 125,
      percentualConsumido: 37.5,
      mes: now.getMonth() + 1,
      ano: now.getFullYear(),
    }] });
    fixture.detectChanges();

    expect(element.textContent).toContain('Saúde');
    expect(element.textContent).toContain('Medicamentos');
    expect(element.textContent).toContain('Ana Silva');
    expect(element.textContent).toContain('200,00');
    expect(element.textContent).toContain('75,00');
    expect(element.textContent).toContain('125,00');
    expect(element.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('37.5');
  });

  it('reloads the monthly totals for the selected month and year', () => {
    const fixture = TestBed.createComponent(BucketsList);
    fixture.detectChanges();
    http.expectOne((item) => item.url === '/api/buckets').flush({ data: [] });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const month = element.querySelector<HTMLSelectElement>('.bucket-period select')!;
    month.value = '12';
    month.dispatchEvent(new Event('change', { bubbles: true }));
    const year = element.querySelector<HTMLInputElement>('.bucket-period input')!;
    year.value = '2025';
    year.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLButtonElement>('.bucket-period button')!.click();
    fixture.detectChanges();

    const request = http.expectOne((item) =>
      item.url === '/api/buckets'
      && item.params.get('mes') === '12'
      && item.params.get('ano') === '2025',
    );
    request.flush({ data: [] });
    fixture.detectChanges();
    expect(element.textContent).toContain('Nenhum bucket cadastrado');
  });
});
