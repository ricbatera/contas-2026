import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PessoaRequest, PessoaService } from './pessoa.service';

describe('PessoaService', () => {
  let service: PessoaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PessoaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const pessoa = { id: 'uuid-1', nome: 'Maria Silva', ativo: true, criadoEm: null };
  const request: PessoaRequest = { nome: pessoa.nome, ativo: true };

  it('lists people from the API envelope', () => {
    service.listar().subscribe((result) => expect(result).toEqual([pessoa]));
    http.expectOne('/api/pessoas').flush({ data: [pessoa] });
  });

  it('loads a person by ID', () => {
    service.buscarPorId('uuid-1').subscribe((result) => expect(result).toEqual(pessoa));
    http.expectOne('/api/pessoas/uuid-1').flush({ data: pessoa });
  });

  it('creates a person', () => {
    service.criar(request).subscribe((result) => expect(result).toEqual(pessoa));
    const req = http.expectOne('/api/pessoas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: pessoa });
  });

  it('updates a person', () => {
    service.atualizar('uuid-1', { nome: 'Maria', ativo: false }).subscribe();
    const req = http.expectOne('/api/pessoas/uuid-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ nome: 'Maria', ativo: false });
    req.flush({ data: { ...pessoa, ativo: false } });
  });

  it('deletes a person', () => {
    service.excluir('uuid-1').subscribe();
    const req = http.expectOne('/api/pessoas/uuid-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
