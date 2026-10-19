import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { calcularVencimento, ComprovanteEdicaoRequest, ComprovanteService } from './comprovante.service';

describe('ComprovanteService', () => {
  let service: ComprovanteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ComprovanteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists receipts filtered by status', () => {
    service.listar('PENDENTE').subscribe((result) => expect(result).toEqual([]));

    http.expectOne('/api/comprovantes?status=PENDENTE').flush({ data: [] });
  });

  it('sends the photo and returns the receipt read by the AI', () => {
    service.enviar({ nomeArquivo: 'foto.jpg', conteudoBase64: 'abc' })
      .subscribe((result) => expect(result.id).toBe('comp-1'));

    const req = http.expectOne('/api/comprovantes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nomeArquivo: 'foto.jpg', conteudoBase64: 'abc' });
    req.flush({ data: { id: 'comp-1' } });
  });

  it('updates, approves, and downloads the image', () => {
    const request: ComprovanteEdicaoRequest = {
      modalidade: 'CREDITO',
      descricao: 'Padaria',
      cartaoId: 'card-1',
      categoriaId: 'cat-1',
      subcategoriaId: 'sub-1',
      dataCompra: '2026-03-20',
      valorTotal: 30,
      quantidadeParcelas: 3,
      primeiroVencimento: '2026-04-25',
    };
    service.atualizar('comp-1', request).subscribe();
    const put = http.expectOne('/api/comprovantes/comp-1');
    expect(put.request.method).toBe('PUT');
    put.flush({ data: { id: 'comp-1' } });

    service.aprovar('comp-1').subscribe((result) => expect(result.status).toBe('APROVADO'));
    http.expectOne('/api/comprovantes/comp-1/aprovar').flush({ data: { id: 'comp-1', status: 'APROVADO' } });

    service.baixarImagem('comp-1').subscribe((imagem) => expect(imagem).toBeInstanceOf(Blob));
    const get = http.expectOne('/api/comprovantes/comp-1/imagem');
    expect(get.request.responseType).toBe('blob');
    get.flush(new Blob(['x'], { type: 'image/jpeg' }));
  });

  it('calculates the first due date from the card closing and due days', () => {
    expect(calcularVencimento('2026-03-14', 15, 25)).toBe('2026-03-25');
    expect(calcularVencimento('2026-03-15', 15, 25)).toBe('2026-04-25');
    expect(calcularVencimento('2026-12-10', 28, 5)).toBe('2027-01-05');
    expect(calcularVencimento('2026-12-29', 28, 5)).toBe('2027-02-05');
    expect(calcularVencimento('2026-02-27', 30, 31)).toBe('2026-02-28');
    expect(calcularVencimento('2026-02-28', 30, 31)).toBe('2026-03-31');
  });
});
