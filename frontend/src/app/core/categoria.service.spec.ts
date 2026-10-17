import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CategoriaService } from './categoria.service';

describe('CategoriaService', () => {
  let service: CategoriaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CategoriaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const categoria = {
    id: 'category-1',
    nome: 'Moradia',
    tipo: 'DESPESA' as const,
    ativo: true,
    padrao: false,
    criadoEm: null,
    subcategorias: [],
  };
  const subcategoria = {
    id: 'subcategory-1',
    nome: 'Aluguel',
    ativo: true,
    padrao: false,
    categoriaId: categoria.id,
    categoriaNome: categoria.nome,
    criadoEm: null,
  };

  it('lists categories and nested subcategories', () => {
    service.listar().subscribe((result) => expect(result).toEqual([categoria]));
    http.expectOne('/api/categorias').flush({ data: [categoria] });
  });

  it('loads a category by ID', () => {
    service.buscarPorId(categoria.id).subscribe((result) => expect(result).toEqual(categoria));
    http.expectOne('/api/categorias/category-1').flush({ data: categoria });
  });

  it('creates and deletes a category', () => {
    service.criar({ nome: 'Moradia', tipo: 'DESPESA', ativo: true }).subscribe();
    const create = http.expectOne('/api/categorias');
    expect(create.request.method).toBe('POST');
    create.flush({ data: categoria });

    service.excluir(categoria.id).subscribe();
    const deletion = http.expectOne('/api/categorias/category-1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
  });

  it('lists subcategories under a category', () => {
    service.listarSubcategorias(categoria.id).subscribe((result) => expect(result).toEqual([subcategoria]));
    http.expectOne('/api/categorias/category-1/subcategorias').flush({ data: [subcategoria] });
  });

  it('creates, loads, updates, and deletes a subcategory', () => {
    const request = { nome: 'Aluguel', ativo: true, padrao: false };
    service.criarSubcategoria(categoria.id, request).subscribe();
    const create = http.expectOne('/api/categorias/category-1/subcategorias');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(request);
    create.flush({ data: subcategoria });

    service.buscarSubcategoria(subcategoria.id).subscribe();
    http.expectOne('/api/subcategorias/subcategory-1').flush({ data: subcategoria });

    service.atualizarSubcategoria(subcategoria.id, {
      ...request,
      categoriaId: categoria.id,
      ativo: false,
    }).subscribe();
    const update = http.expectOne('/api/subcategorias/subcategory-1');
    expect(update.request.method).toBe('PUT');
    update.flush({ data: { ...subcategoria, ativo: false } });

    service.excluirSubcategoria(subcategoria.id).subscribe();
    const deletion = http.expectOne('/api/subcategorias/subcategory-1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
  });
});
