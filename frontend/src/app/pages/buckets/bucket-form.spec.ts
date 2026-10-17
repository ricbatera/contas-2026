import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { BucketForm } from './bucket-form';

describe('BucketForm', () => {
  let http: HttpTestingController;
  let bucketId: string | null;

  beforeEach(() => {
    bucketId = null;
    TestBed.configureTestingModule({
      imports: [BucketForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (key: string) => (key === 'id' ? bucketId : null) } } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a monthly bucket assigned to the signed-in user', async () => {
    const fixture = TestBed.createComponent(BucketForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne('/api/categorias').flush({ data: [{
      id: 'health',
      nome: 'Saúde',
      tipo: 'DESPESA',
      ativo: true,
      padrao: false,
      criadoEm: null,
      subcategorias: [{
        id: 'medicine',
        nome: 'Medicamentos',
        ativo: true,
        padrao: false,
        categoriaId: 'health',
        categoriaNome: 'Saúde',
        criadoEm: null,
      }],
    }] });
    http.expectOne('/api/pessoas').flush({ data: [] });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLInputElement>('[formControlName="valorMensal"]')!.value = '200';
    element.querySelector<HTMLInputElement>('[formControlName="valorMensal"]')!
      .dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const creation = http.expectOne('/api/buckets');
    expect(creation.request.method).toBe('POST');
    expect(creation.request.body).toEqual({
      subcategoriaId: 'medicine',
      responsavelPessoaId: null,
      valorMensal: 200,
    });
    creation.flush({ data: {} });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/buckets']);
  });

  it('requires an amount above zero', () => {
    const fixture = TestBed.createComponent(BucketForm);
    fixture.detectChanges();
    http.expectOne('/api/categorias').flush({ data: [] });
    http.expectOne('/api/pessoas').flush({ data: [] });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();

    expect(element.textContent).toContain('Informe um valor mensal maior que zero');
    http.expectNone('/api/buckets');
  });

  it('assigns the budget to a selected person', async () => {
    const fixture = TestBed.createComponent(BucketForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne('/api/categorias').flush({ data: [{
      id: 'health',
      nome: 'Saúde',
      tipo: 'DESPESA',
      ativo: true,
      padrao: false,
      criadoEm: null,
      subcategorias: [{
        id: 'medicine',
        nome: 'Medicamentos',
        ativo: true,
        padrao: false,
        categoriaId: 'health',
        categoriaNome: 'Saúde',
        criadoEm: null,
      }],
    }] });
    http.expectOne('/api/pessoas').flush({
      data: [{ id: 'person-1', nome: 'Maria', ativo: true, criadoEm: null }],
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const responsible = element.querySelectorAll<HTMLSelectElement>('.bucket-form select')[2]!;
    responsible.value = 'person-1';
    responsible.dispatchEvent(new Event('change', { bubbles: true }));
    const amount = element.querySelector<HTMLInputElement>('[formControlName="valorMensal"]')!;
    amount.value = '150';
    amount.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const creation = http.expectOne('/api/buckets');
    expect(creation.request.body).toEqual({
      subcategoriaId: 'medicine',
      responsavelPessoaId: 'person-1',
      valorMensal: 150,
    });
    creation.flush({ data: {} });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/buckets']);
  });
});
