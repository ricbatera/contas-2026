import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { SubcategoriaForm } from './subcategoria-form';

describe('SubcategoriaForm', () => {
  let http: HttpTestingController;
  let routeId: string | null;
  let categoryId: string | null;

  beforeEach(() => {
    routeId = null;
    categoryId = 'category-1';
    TestBed.configureTestingModule({
      imports: [SubcategoriaForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? routeId : key === 'categoriaId' ? categoryId : null),
              },
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const category = {
    id: 'category-1',
    nome: 'Moradia',
    tipo: 'DESPESA',
    ativo: true,
    padrao: false,
    criadoEm: null,
    subcategorias: [],
  };

  it('creates a subcategory under the selected category', async () => {
    const fixture = TestBed.createComponent(SubcategoriaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne('/api/categorias/category-1').flush({ data: category });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = 'Aluguel';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLInputElement>('[formControlName="padrao"]')!.click();
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const create = http.expectOne('/api/categorias/category-1/subcategorias');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ nome: 'Aluguel', ativo: true, padrao: true });
    create.flush({
      data: {
        id: 'subcategory-1',
        nome: 'Aluguel',
        ativo: true,
        padrao: true,
        categoriaId: 'category-1',
        categoriaNome: 'Moradia',
        criadoEm: null,
      },
    });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/categorias']);
  });

  it('loads the parent category and clears default when the subcategory is deactivated', async () => {
    routeId = 'subcategory-1';
    categoryId = null;
    const fixture = TestBed.createComponent(SubcategoriaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne('/api/subcategorias/subcategory-1').flush({
      data: {
        id: 'subcategory-1',
        nome: 'Aluguel',
        ativo: true,
        padrao: true,
        categoriaId: 'category-1',
        categoriaNome: 'Moradia',
        criadoEm: null,
      },
    });
    http.expectOne('/api/categorias/category-1').flush({ data: category });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Moradia');
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = 'Aluguel residencial';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLInputElement>('[formControlName="ativo"]')!.click();
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const update = http.expectOne('/api/subcategorias/subcategory-1');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({
      nome: 'Aluguel residencial',
      ativo: false,
      padrao: false,
      categoriaId: 'category-1',
    });
    update.flush({
      data: {
        id: 'subcategory-1',
        nome: 'Aluguel residencial',
        ativo: false,
        padrao: false,
        categoriaId: 'category-1',
        categoriaNome: 'Moradia',
        criadoEm: null,
      },
    });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/categorias']);
  });
});
