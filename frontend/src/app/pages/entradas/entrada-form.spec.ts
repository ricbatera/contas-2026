import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { EntradaForm } from './entrada-form';

describe('EntradaForm', () => {
  let http: HttpTestingController;
  let routeId: string | null;

  beforeEach(() => {
    routeId = null;
    TestBed.configureTestingModule({
      imports: [EntradaForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (key: string) => (key === 'id' ? routeId : null) } } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function flushDependencies(): void {
    http.expectOne('/api/categorias').flush({
      data: [
        {
          id: 'expense-category',
          nome: 'Moradia',
          tipo: 'DESPESA',
          ativo: true,
          padrao: true,
          criadoEm: null,
          subcategorias: [{ id: 'expense-sub', nome: 'Aluguel', ativo: true, padrao: true,
            categoriaId: 'expense-category', categoriaNome: 'Moradia', criadoEm: null }],
        },
        {
          id: 'income-category',
          nome: 'Salário',
          tipo: 'RECEITA',
          ativo: true,
          padrao: true,
          criadoEm: null,
          subcategorias: [{ id: 'income-sub', nome: 'Salário mensal', ativo: true, padrao: true,
            categoriaId: 'income-category', categoriaNome: 'Salário', criadoEm: null }],
        },
      ],
    });
    http.expectOne('/api/contas-bancarias').flush({
      data: [{ id: 'account-1', nome: 'Conta corrente', banco: 'Banco', tipoConta: 'CORRENTE', agencia: null,
        numeroConta: null, saldoInicial: 0, cor: null, ativo: true, criadoEm: null }],
    });
  }

  function preencher(element: HTMLElement, campo: string, valor: string, evento = 'input'): void {
    const control = element.querySelector<HTMLInputElement | HTMLSelectElement>(`[formControlName="${campo}"]`);
    if (!control) {
      throw new Error(`Campo "${campo}" não encontrado.`);
    }
    control.value = valor;
    control.dispatchEvent(new Event(evento, { bubbles: true }));
  }

  it('offers only income categories and submits a recurring entry', async () => {
    const fixture = TestBed.createComponent(EntradaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    flushDependencies();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).not.toContain('Moradia');
    expect(element.querySelector<HTMLSelectElement>('[formControlName="categoriaId"]')?.value).toBe('income-category');
    expect(element.querySelector('[formControlName="dataFinal"]')).toBeNull();

    preencher(element, 'descricao', 'Salário');
    preencher(element, 'valor', '5000');
    const recorrente = element.querySelector<HTMLInputElement>('[formControlName="recorrente"]')!;
    recorrente.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Primeiro recebimento');
    preencher(element, 'dataInicial', '2026-10-05');
    preencher(element, 'dataFinal', '2027-12-05');
    preencher(element, 'contaBancariaId', 'account-1', 'change');
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const request = http.expectOne('/api/entradas');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      descricao: 'Salário',
      valor: 5000,
      recorrente: true,
      dataInicial: '2026-10-05',
      dataFinal: '2027-12-05',
      categoriaId: 'income-category',
      subcategoriaId: 'income-sub',
      contaBancariaId: 'account-1',
    });
    request.flush({ data: {} });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/entradas']);
  });

  it('loads an existing entry when editing', () => {
    routeId = 'income-1';
    const fixture = TestBed.createComponent(EntradaForm);
    fixture.detectChanges();
    flushDependencies();
    http.expectOne('/api/entradas/income-1').flush({
      data: {
        id: 'income-1',
        descricao: 'Aluguel recebido',
        valor: 1500,
        recorrente: true,
        dataInicial: '2026-01-10',
        dataFinal: null,
        categoriaId: 'income-category',
        categoriaNome: 'Salário',
        subcategoriaId: 'income-sub',
        subcategoriaNome: 'Salário mensal',
        contaBancariaId: null,
        contaBancariaNome: null,
        criadoEm: null,
      },
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector<HTMLInputElement>('[formControlName="descricao"]')?.value).toBe('Aluguel recebido');
    expect(element.querySelector<HTMLInputElement>('[formControlName="recorrente"]')?.checked).toBe(true);
    expect(element.querySelector<HTMLSelectElement>('[formControlName="subcategoriaId"]')?.value).toBe('income-sub');
    expect(element.textContent).toContain('Salvar alterações');
  });

  it('creates an income category through the quick dialog', () => {
    const fixture = TestBed.createComponent(EntradaForm);
    fixture.detectChanges();
    flushDependencies();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('[aria-label="Cadastrar nova categoria"]')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Nova categoria de receita');
    const dialogo = element.querySelector<HTMLElement>('app-categoria-rapida')!;
    preencher(dialogo, 'nome', 'Freelas');
    preencher(dialogo, 'subcategoria', 'Design');
    dialogo.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const categoria = http.expectOne('/api/categorias');
    expect(categoria.request.body).toEqual({ nome: 'Freelas', tipo: 'RECEITA', ativo: true });
    categoria.flush({
      data: { id: 'category-2', nome: 'Freelas', tipo: 'RECEITA', ativo: true, padrao: false, criadoEm: null, subcategorias: [] },
    });
    http.expectOne('/api/categorias/category-2/subcategorias').flush({
      data: { id: 'sub-2', nome: 'Design', ativo: true, padrao: true, categoriaId: 'category-2', categoriaNome: 'Freelas', criadoEm: null },
    });
    fixture.detectChanges();

    expect(element.querySelector<HTMLSelectElement>('[formControlName="categoriaId"]')?.value).toBe('category-2');
    expect(element.querySelector<HTMLSelectElement>('[formControlName="subcategoriaId"]')?.value).toBe('sub-2');
  });
});
