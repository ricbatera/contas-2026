import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { CategoriasList } from './categorias-list';

describe('CategoriasList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CategoriasList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const categorias = [
    {
      id: 'category-3',
      nome: 'Compras',
      tipo: 'DESPESA',
      ativo: true,
      padrao: false,
      criadoEm: null,
      subcategorias: [],
    },
    {
      id: 'category-1',
      nome: 'Moradia',
      tipo: 'DESPESA',
      ativo: true,
      padrao: true,
      criadoEm: null,
      subcategorias: [
        {
          id: 'subcategory-1',
          nome: 'Aluguel',
          ativo: true,
          padrao: true,
          categoriaId: 'category-1',
          categoriaNome: 'Moradia',
          criadoEm: null,
        },
      ],
    },
    {
      id: 'category-2',
      nome: 'Salário',
      tipo: 'RECEITA',
      ativo: false,
      padrao: false,
      criadoEm: null,
      subcategorias: [],
    },
  ];

  it('shows categories and subcategories in the selected type tab', () => {
    const fixture = TestBed.createComponent(CategoriasList);
    fixture.detectChanges();
    http.expectOne('/api/categorias').flush({ data: categorias });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent ?? '';
    expect(text).toContain('Despesas');
    expect(text).toContain('Moradia');
    expect(text).toContain('Aluguel');
    expect(text).toContain('Padrão');
    expect(text).not.toContain('Salário');
    const tabs = element.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    expect(tabs).toHaveLength(2);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    const expenseNames = Array.from(element.querySelectorAll('.category-card .category-title h4'))
      .map((heading) => heading.textContent?.trim());
    expect(expenseNames).toEqual(['Moradia', 'Compras']);

    tabs[1].click();
    fixture.detectChanges();
    const updatedText = element.textContent ?? '';
    expect(updatedText).toContain('Salário');
    expect(updatedText).not.toContain('Moradia');
    expect(updatedText).toContain('Inativa');
    expect(tabs[1].getAttribute('aria-selected')).toBe('true');
  });

  it('shows an empty state when no categories exist', () => {
    const fixture = TestBed.createComponent(CategoriasList);
    fixture.detectChanges();
    http.expectOne('/api/categorias').flush({ data: [] });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Nenhuma categoria de despesas');
    element.querySelector<HTMLButtonElement>('[role="tab"]:nth-child(2)')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Nenhuma categoria de receitas');
  });

  it('confirms cascade deletion and removes the category from the list', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(CategoriasList);
    fixture.detectChanges();
    http.expectOne('/api/categorias').flush({ data: categorias });
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[aria-label="Excluir categoria Moradia"]')!
      .click();
    expect(
      (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('[aria-label="Excluir categoria Moradia"]')
        ?.classList.contains('category-delete-button'),
    ).toBe(true);
    expect(confirm).toHaveBeenCalledOnce();
    expect(confirm.mock.calls[0][0]).toContain('subcategorias também serão excluídas');
    const deletion = http.expectOne('/api/categorias/category-1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Moradia');
    expect(text).toContain('Compras');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.category-card')).toHaveLength(1);
    confirm.mockRestore();
  });

  it('confirms and deletes a subcategory without removing its parent', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(CategoriasList);
    fixture.detectChanges();
    http.expectOne('/api/categorias').flush({ data: categorias });
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[aria-label="Excluir subcategoria Aluguel"]')!
      .click();
    const deletion = http.expectOne('/api/subcategorias/subcategory-1');
    deletion.flush(null);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Moradia');
    expect(text).not.toContain('Aluguel');
    confirm.mockRestore();
  });
});
