import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { EntradaOcorrencia } from '../../core/entrada.service';
import { EntradasList } from './entradas-list';

function ocorrencia(overrides: Partial<EntradaOcorrencia>): EntradaOcorrencia {
  return {
    id: 'occurrence-1',
    entradaId: 'income-1',
    descricao: 'Salário',
    recorrente: true,
    categoriaId: 'category-1',
    categoriaNome: 'Salário',
    subcategoriaId: 'subcategory-1',
    subcategoriaNome: 'Salário mensal',
    contaBancariaId: 'account-1',
    contaBancariaNome: 'Conta corrente',
    competencia: '2026-10-01',
    dataPrevista: '2026-10-05',
    valorPrevisto: 5000,
    status: 'PREVISTA',
    valorRecebido: null,
    dataRecebimento: null,
    ...overrides,
  };
}

describe('EntradasList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EntradasList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function esperarOcorrencias(): TestRequest {
    return http.expectOne((req) => req.url === '/api/entradas/ocorrencias');
  }

  function botao(element: HTMLElement, texto: string): HTMLButtonElement {
    const encontrado = Array.from(element.querySelectorAll('button'))
      .find((item) => item.textContent?.trim() === texto);
    if (!encontrado) {
      throw new Error(`Botão "${texto}" não encontrado.`);
    }
    return encontrado;
  }

  it('loads the current month and shows forecast and received totals', () => {
    const fixture = TestBed.createComponent(EntradasList);
    fixture.detectChanges();
    const request = esperarOcorrencias();
    const hoje = new Date();
    expect(request.request.params.get('mes')).toBe(String(hoje.getMonth() + 1));
    expect(request.request.params.get('ano')).toBe(String(hoje.getFullYear()));
    request.flush({
      data: [
        ocorrencia({}),
        ocorrencia({ id: 'occurrence-2', entradaId: 'income-2', descricao: 'Freela', recorrente: false,
          valorPrevisto: 800, status: 'RECEBIDA', valorRecebido: 750, dataRecebimento: '2026-10-10' }),
        ocorrencia({ id: 'occurrence-3', entradaId: 'income-3', descricao: 'Bônus', valorPrevisto: 300,
          status: 'CANCELADA' }),
      ],
    });
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
    // O locale dos testes não é pt-BR: o separador decimal varia.
    expect(texto).toMatch(/Previsto: R\$\s?5[.,]750[.,]00/);
    expect(texto).toMatch(/Recebido: R\$\s?750[.,]00/);
    expect(texto).toContain('Recorrente');
    expect(texto).toContain('Cancelada');
  });

  it('navigates to the next month', () => {
    const fixture = TestBed.createComponent(EntradasList);
    fixture.detectChanges();
    esperarOcorrencias().flush({ data: [] });
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[aria-label="Próximo mês"]')!.click();
    const proximo = new Date(new Date().getFullYear(), new Date().getMonth() + 1, 1);
    const request = esperarOcorrencias();
    expect(request.request.params.get('mes')).toBe(String(proximo.getMonth() + 1));
    expect(request.request.params.get('ano')).toBe(String(proximo.getFullYear()));
    request.flush({ data: [] });
  });

  it('registers the receipt of a forecast occurrence', () => {
    const fixture = TestBed.createComponent(EntradasList);
    fixture.detectChanges();
    esperarOcorrencias().flush({ data: [ocorrencia({})] });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    botao(element, 'Receber').click();
    fixture.detectChanges();
    const valor = element.querySelector<HTMLInputElement>('[formControlName="valorRecebido"]')!;
    expect(Number(valor.value)).toBe(5000);
    valor.value = '4900';
    valor.dispatchEvent(new Event('input', { bubbles: true }));
    const data = element.querySelector<HTMLInputElement>('[formControlName="dataRecebimento"]')!;
    data.value = '2026-10-06';
    data.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('.entrada-receive-dialog form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const request = http.expectOne('/api/entradas/ocorrencias/occurrence-1/receber');
    expect(request.request.body).toEqual({ valorRecebido: 4900, dataRecebimento: '2026-10-06' });
    request.flush({
      data: ocorrencia({ status: 'RECEBIDA', valorRecebido: 4900, dataRecebimento: '2026-10-06' }),
    });
    fixture.detectChanges();

    expect(element.querySelector('.entrada-receive-dialog')).toBeNull();
    expect(element.textContent).toMatch(/Recebido: R\$\s?4[.,]900[.,]00 em/);
    expect(botao(element, 'Reabrir')).toBeTruthy();
  });

  it('deletes the whole entry after confirmation', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(EntradasList);
    fixture.detectChanges();
    esperarOcorrencias().flush({ data: [ocorrencia({})] });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    botao(element, 'Excluir').click();
    const request = http.expectOne('/api/entradas/income-1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    fixture.detectChanges();

    expect(element.textContent).toContain('Nenhuma entrada em');
  });
});
