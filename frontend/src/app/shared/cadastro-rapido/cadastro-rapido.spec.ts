import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CartaoRapido } from './cartao-rapido';
import { CategoriaRapida } from './categoria-rapida';
import { PessoaRapida } from './pessoa-rapida';
import { SubcategoriaRapida } from './subcategoria-rapida';

describe('Cadastro rápido', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function preencher(fixture: ComponentFixture<unknown>, controle: string, valor: string): void {
    const campo = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLInputElement | HTMLSelectElement>(`[formControlName="${controle}"]`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  }

  function enviar(fixture: ComponentFixture<unknown>): void {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
  }

  it('creates a person and emits it', () => {
    const fixture = TestBed.createComponent(PessoaRapida);
    const salvo = vi.fn();
    fixture.componentInstance.salvo.subscribe(salvo);
    fixture.detectChanges();

    preencher(fixture, 'nome', '  João  ');
    enviar(fixture);

    const request = http.expectOne('/api/pessoas');
    expect(request.request.body).toEqual({ nome: 'João', ativo: true });
    request.flush({ data: { id: 'person-2', nome: 'João', ativo: true, criadoEm: null } });
    expect(salvo).toHaveBeenCalledWith({ id: 'person-2', nome: 'João', ativo: true, criadoEm: null });
  });

  it('does not submit an empty person name and shows API errors', () => {
    const fixture = TestBed.createComponent(PessoaRapida);
    fixture.detectChanges();
    enviar(fixture);
    http.expectNone('/api/pessoas');

    preencher(fixture, 'nome', 'Maria');
    enviar(fixture);
    http.expectOne('/api/pessoas').flush(
      { message: 'Já existe uma pessoa com esse nome' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Já existe uma pessoa com esse nome');
  });

  it('creates a subcategory inside the given category', () => {
    const fixture = TestBed.createComponent(SubcategoriaRapida);
    fixture.componentRef.setInput('categoria', {
      id: 'category-1',
      nome: 'Moradia',
      tipo: 'DESPESA',
      ativo: true,
      padrao: false,
      criadoEm: null,
      subcategorias: [],
    });
    const salvo = vi.fn();
    fixture.componentInstance.salvo.subscribe(salvo);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Moradia');

    preencher(fixture, 'nome', 'Condomínio');
    enviar(fixture);

    const request = http.expectOne('/api/categorias/category-1/subcategorias');
    expect(request.request.body).toEqual({ nome: 'Condomínio', ativo: true, padrao: false });
    request.flush({ data: { id: 'sub-2', nome: 'Condomínio', categoriaId: 'category-1' } });
    expect(salvo).toHaveBeenCalledWith(expect.objectContaining({ id: 'sub-2' }));
  });

  it('creates an expense category with its first subcategory and retries only the subcategory', () => {
    const fixture = TestBed.createComponent(CategoriaRapida);
    const salvo = vi.fn();
    fixture.componentInstance.salvo.subscribe(salvo);
    fixture.detectChanges();

    preencher(fixture, 'nome', 'Pets');
    preencher(fixture, 'subcategoria', 'Ração');
    enviar(fixture);

    const categoria = http.expectOne('/api/categorias');
    expect(categoria.request.body).toEqual({ nome: 'Pets', tipo: 'DESPESA', ativo: true });
    categoria.flush({
      data: { id: 'category-2', nome: 'Pets', tipo: 'DESPESA', ativo: true, padrao: false, criadoEm: null, subcategorias: [] },
    });
    const falha = http.expectOne('/api/categorias/category-2/subcategorias');
    expect(falha.request.body).toEqual({ nome: 'Ração', ativo: true, padrao: true });
    falha.flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('A categoria "Pets" foi criada');
    expect(salvo).not.toHaveBeenCalled();

    enviar(fixture);
    http.expectNone('/api/categorias');
    http.expectOne('/api/categorias/category-2/subcategorias').flush({
      data: { id: 'sub-3', nome: 'Ração', ativo: true, padrao: true, categoriaId: 'category-2', categoriaNome: 'Pets', criadoEm: null },
    });
    expect(salvo).toHaveBeenCalledWith(expect.objectContaining({
      id: 'category-2',
      subcategorias: [expect.objectContaining({ id: 'sub-3', padrao: true })],
    }));
  });

  it('creates an additional card linked to its main card', () => {
    const fixture = TestBed.createComponent(CartaoRapido);
    const salvo = vi.fn();
    fixture.componentInstance.salvo.subscribe(salvo);
    fixture.detectChanges();
    http.expectOne('/api/cartoes/principais').flush({
      data: [{ id: 'card-1', nome: 'Principal', banco: 'Banco', ultimosDigitos: '1234', ativo: true }],
    });

    preencher(fixture, 'nome', 'Adicional');
    preencher(fixture, 'banco', 'Banco');
    preencher(fixture, 'ultimosDigitos', '9876');
    preencher(fixture, 'tipo', 'ADICIONAL');
    fixture.detectChanges();
    enviar(fixture);
    http.expectNone('/api/cartoes');

    preencher(fixture, 'cartaoPrincipalId', 'card-1');
    enviar(fixture);

    const request = http.expectOne('/api/cartoes');
    expect(request.request.body).toEqual({
      nome: 'Adicional',
      banco: 'Banco',
      bandeira: 'VISA',
      ultimosDigitos: '9876',
      cor: '#1e6fd0',
      tipo: 'ADICIONAL',
      virtual: false,
      ativo: true,
      cartaoPrincipalId: 'card-1',
    });
    request.flush({ data: { id: 'card-2', nome: 'Adicional', ativo: true } });
    expect(salvo).toHaveBeenCalledWith(expect.objectContaining({ id: 'card-2' }));
  });

  it('emits cancel when the backdrop is clicked', () => {
    const fixture = TestBed.createComponent(PessoaRapida);
    const cancelado = vi.fn();
    fixture.componentInstance.cancelado.subscribe(cancelado);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.filter-backdrop')!.click();
    expect(cancelado).toHaveBeenCalled();
  });
});
