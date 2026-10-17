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

  describe('cadastro rápido', () => {
    function abrirFormulario() {
      const fixture = TestBed.createComponent(BucketForm);
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
      return { fixture, element: fixture.nativeElement as HTMLElement };
    }

    function preencherDialogo(element: HTMLElement, valores: Record<string, string>): void {
      for (const [controle, valor] of Object.entries(valores)) {
        const campo = element.querySelector<HTMLInputElement>(`[role="dialog"] [formControlName="${controle}"]`)!;
        campo.value = valor;
        campo.dispatchEvent(new Event('input', { bubbles: true }));
      }
      element.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(
        new Event('submit', { bubbles: true, cancelable: true }),
      );
    }

    function selects(element: HTMLElement): HTMLSelectElement[] {
      return Array.from(element.querySelectorAll<HTMLSelectElement>('.bucket-form select'));
    }

    it('creates a category with its subcategory and submits the bucket with them', () => {
      vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      const { fixture, element } = abrirFormulario();
      element.querySelector<HTMLButtonElement>('button[aria-label="Cadastrar nova categoria"]')!.click();
      fixture.detectChanges();
      preencherDialogo(element, { nome: 'Pets', subcategoria: 'Ração' });
      http.expectOne('/api/categorias').flush({
        data: { id: 'pets', nome: 'Pets', tipo: 'DESPESA', ativo: true, padrao: false, criadoEm: null, subcategorias: [] },
      });
      http.expectOne('/api/categorias/pets/subcategorias').flush({
        data: { id: 'food', nome: 'Ração', ativo: true, padrao: true, categoriaId: 'pets', categoriaNome: 'Pets', criadoEm: null },
      });
      fixture.detectChanges();

      expect(element.querySelector('[role="dialog"]')).toBeNull();
      expect(selects(element)[0].value).toBe('pets');
      expect(selects(element)[1].value).toBe('food');

      const amount = element.querySelector<HTMLInputElement>('[formControlName="valorMensal"]')!;
      amount.value = '80';
      amount.dispatchEvent(new Event('input', { bubbles: true }));
      element.querySelector<HTMLFormElement>('.bucket-form')!.dispatchEvent(
        new Event('submit', { bubbles: true, cancelable: true }),
      );
      const creation = http.expectOne('/api/buckets');
      expect(creation.request.body).toEqual({ subcategoriaId: 'food', responsavelPessoaId: null, valorMensal: 80 });
      creation.flush({ data: {} });
    });

    it('creates a subcategory in the selected category and selects it', () => {
      const { fixture, element } = abrirFormulario();
      element.querySelector<HTMLButtonElement>('button[aria-label="Cadastrar nova subcategoria"]')!.click();
      fixture.detectChanges();
      expect(element.querySelector('[role="dialog"]')?.textContent).toContain('Saúde');
      preencherDialogo(element, { nome: 'Consultas' });
      http.expectOne('/api/categorias/health/subcategorias').flush({
        data: { id: 'visits', nome: 'Consultas', ativo: true, padrao: false, categoriaId: 'health', categoriaNome: 'Saúde', criadoEm: null },
      });
      fixture.detectChanges();

      expect(selects(element)[1].value).toBe('visits');
    });

    it('creates a person and makes them responsible for the bucket', () => {
      const { fixture, element } = abrirFormulario();
      element.querySelector<HTMLButtonElement>('button[aria-label="Cadastrar nova pessoa"]')!.click();
      fixture.detectChanges();
      preencherDialogo(element, { nome: 'João' });
      http.expectOne('/api/pessoas').flush({ data: { id: 'person-2', nome: 'João', ativo: true, criadoEm: null } });
      fixture.detectChanges();

      expect(selects(element)[2].value).toBe('person-2');
    });
  });
});
