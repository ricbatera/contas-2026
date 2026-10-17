import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FaturaImportacaoService, lerArquivoBase64 } from './fatura-importacao.service';

describe('FaturaImportacaoService', () => {
  let service: FaturaImportacaoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FaturaImportacaoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uploads the statement PDF as base64 with the optional password', () => {
    const request = { nomeArquivo: 'fatura.pdf', conteudoBase64: 'JVBERi0=', senha: '1234' };

    service.importar(request).subscribe((result) => expect(result.id).toBe('imp-1'));

    const req = http.expectOne('/api/faturas/importacoes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: { id: 'imp-1' } });
  });

  it('lists, loads, reprocesses and deletes imports', () => {
    service.listar().subscribe((result) => expect(result).toEqual([]));
    http.expectOne('/api/faturas/importacoes').flush({ data: [] });

    service.buscarPorId('imp 1').subscribe((result) => expect(result.id).toBe('imp 1'));
    http.expectOne('/api/faturas/importacoes/imp%201').flush({ data: { id: 'imp 1' } });

    service.reprocessar('imp-1').subscribe();
    const reprocessar = http.expectOne('/api/faturas/importacoes/imp-1/reprocessar');
    expect(reprocessar.request.method).toBe('POST');
    reprocessar.flush({ data: { id: 'imp-1' } });

    service.excluir('imp-1').subscribe();
    const excluir = http.expectOne('/api/faturas/importacoes/imp-1');
    expect(excluir.request.method).toBe('DELETE');
    excluir.flush(null);
  });

  it('reads the lightweight processing status', () => {
    service.buscarStatus('imp-1').subscribe((result) => expect(result.status).toBe('PROCESSANDO'));

    http.expectOne('/api/faturas/importacoes/imp-1/status').flush({ data: { id: 'imp-1', status: 'PROCESSANDO' } });
  });

  it('downloads the stored PDF as a blob', () => {
    service.baixarPdf('imp-1').subscribe((result) => expect(result).toBeInstanceOf(Blob));

    const req = http.expectOne('/api/faturas/importacoes/imp-1/pdf');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF-'], { type: 'application/pdf' }));
  });

  it('reads a file as base64 without the data URL prefix', async () => {
    const base64 = await lerArquivoBase64(new Blob(['%PDF-'], { type: 'application/pdf' }));

    expect(base64).toBe(btoa('%PDF-'));
  });
});
