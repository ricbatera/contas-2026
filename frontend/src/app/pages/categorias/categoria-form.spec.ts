import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { CategoriaForm } from './categoria-form';

describe('CategoriaForm', () => {
  let http: HttpTestingController;
  let routeId: string | null;

  beforeEach(() => {
    routeId = null;
    TestBed.configureTestingModule({
      imports: [CategoriaForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: (key: string) => (key === 'id' ? routeId : null) },
              queryParamMap: { get: () => 'RECEITA' },
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a category with the selected type', async () => {
    const fixture = TestBed.createComponent(CategoriaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = 'Salário';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const create = http.expectOne('/api/categorias');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ nome: 'Salário', tipo: 'RECEITA', ativo: true });
    create.flush({
      data: {
        id: 'category-1',
        nome: 'Salário',
        tipo: 'RECEITA',
        ativo: true,
        padrao: false,
        subcategorias: [],
      },
    });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/categorias']);
  });

  it('rejects blank names', () => {
    const fixture = TestBed.createComponent(CategoriaForm);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = '  ';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();
    expect(element.textContent).toContain('Informe um nome válido');
    http.expectNone('/api/categorias');
  });
});
