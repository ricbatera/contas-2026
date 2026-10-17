import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FaturaImportacaoStatus } from './fatura-importacao.service';
import { ImportacaoMonitorService } from './importacao-monitor.service';

describe('ImportacaoMonitorService', () => {
  let monitor: ImportacaoMonitorService;
  let http: HttpTestingController;

  const processando: FaturaImportacaoStatus = {
    id: 'imp-1',
    nomeArquivo: 'fatura.pdf',
    status: 'PROCESSANDO',
    erro: null,
    preSaidasPendentes: 0,
    atualizadoEm: null,
  };

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    monitor = TestBed.inject(ImportacaoMonitorService);
    http = TestBed.inject(HttpTestingController);
    monitor.iniciar();
    http.expectOne('/api/faturas/importacoes').flush({ data: [] });
  });

  afterEach(() => {
    monitor.parar();
    http.verify();
    vi.useRealTimers();
  });

  it('polls the status until processing finishes and then raises an alert', () => {
    monitor.acompanhar(processando);
    expect(monitor.emProcessamento()).toHaveLength(1);

    vi.advanceTimersByTime(ImportacaoMonitorService.INTERVALO_MS);
    http.expectOne('/api/faturas/importacoes/imp-1/status').flush({ data: processando });
    expect(monitor.alertas()).toEqual([]);

    vi.advanceTimersByTime(ImportacaoMonitorService.INTERVALO_MS);
    http.expectOne('/api/faturas/importacoes/imp-1/status').flush({
      data: { ...processando, status: 'CONCLUIDA', preSaidasPendentes: 3 },
    });

    expect(monitor.emProcessamento()).toEqual([]);
    expect(monitor.versao()).toBe(1);
    expect(monitor.alertas()).toEqual([
      { id: 'imp-1', nomeArquivo: 'fatura.pdf', sucesso: true, preSaidasPendentes: 3, erro: null },
    ]);

    // Nada mais em processamento: o monitor para de consultar.
    vi.advanceTimersByTime(ImportacaoMonitorService.INTERVALO_MS * 3);
    http.expectNone('/api/faturas/importacoes/imp-1/status');

    monitor.dispensar('imp-1');
    expect(monitor.alertas()).toEqual([]);
  });

  it('resumes imports that were still processing when the shell starts', () => {
    monitor.parar();
    monitor.iniciar();
    http.expectOne('/api/faturas/importacoes').flush({
      data: [
        { ...processando, cartaoPrincipalId: null, cartaoNome: null, cartaoUltimosDigitos: null, competencia: null,
          vencimento: null, totalPdf: null, criadoEm: '2026-10-17T10:00:00' },
        { ...processando, id: 'imp-2', status: 'CONCLUIDA', cartaoPrincipalId: null, cartaoNome: null,
          cartaoUltimosDigitos: null, competencia: null, vencimento: null, totalPdf: null, criadoEm: '2026-10-17T10:00:00' },
      ],
    });

    expect(monitor.emProcessamento().map((item) => item.id)).toEqual(['imp-1']);
    vi.advanceTimersByTime(ImportacaoMonitorService.INTERVALO_MS);
    http.expectOne('/api/faturas/importacoes/imp-1/status').flush({
      data: { ...processando, status: 'ERRO', erro: 'Falha na IA' },
    });
    expect(monitor.alertas()[0]).toMatchObject({ sucesso: false, erro: 'Falha na IA' });
  });

  it('skips the floating alert for the import open on the review screen', () => {
    monitor.visualizando.set('imp-1');
    monitor.acompanhar(processando);

    vi.advanceTimersByTime(ImportacaoMonitorService.INTERVALO_MS);
    http.expectOne('/api/faturas/importacoes/imp-1/status').flush({ data: { ...processando, status: 'CONCLUIDA' } });

    expect(monitor.statusDe('imp-1')?.status).toBe('CONCLUIDA');
    expect(monitor.alertas()).toEqual([]);
  });

  it('keeps polling after a transient error and forgets deleted imports', () => {
    monitor.acompanhar(processando);
    monitor.acompanhar({ ...processando, id: 'imp-2' });

    vi.advanceTimersByTime(ImportacaoMonitorService.INTERVALO_MS);
    http.expectOne('/api/faturas/importacoes/imp-1/status').flush(null, { status: 500, statusText: 'Erro' });
    http.expectOne('/api/faturas/importacoes/imp-2/status').flush(null, { status: 404, statusText: 'Não encontrado' });

    expect(monitor.emProcessamento().map((item) => item.id)).toEqual(['imp-1']);
    vi.advanceTimersByTime(ImportacaoMonitorService.INTERVALO_MS);
    http.expectOne('/api/faturas/importacoes/imp-1/status').flush({ data: processando });
  });
});
