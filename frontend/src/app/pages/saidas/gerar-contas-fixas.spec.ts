import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ContaFixaPrevia } from '../../core/saida.service';
import { GerarContasFixas } from './gerar-contas-fixas';

function previa(overrides: Partial<ContaFixaPrevia>): ContaFixaPrevia {
  return {
    serieId: 'serie-1',
    saidaOrigemId: 'saida-1',
    descricao: 'Internet',
    categoriaNome: 'Casa',
    subcategoriaNome: 'Serviços',
    formaPagamento: 'BOLETO',
    modoResponsabilidade: 'USUARIO',
    responsaveis: ['Ana Silva'],
    valorPorParcela: 100,
    dataSugerida: '2026-11-10',
    aviso: null,
    ...overrides,
  };
}

describe('GerarContasFixas', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GerarContasFixas],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function criar(itens: ContaFixaPrevia[]): ComponentFixture<GerarContasFixas> {
    const fixture = TestBed.createComponent(GerarContasFixas);
    fixture.componentRef.setInput('itens', itens);
    fixture.componentRef.setInput('mes', 11);
    fixture.componentRef.setInput('ano', 2026);
    fixture.detectChanges();
    return fixture;
  }

  function digitar(input: HTMLInputElement, valor: string): void {
    input.value = valor;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  it('shows the month and preselects only the items without a warning', () => {
    const fixture = criar([
      previa({}),
      previa({ serieId: 'serie-2', descricao: 'Academia', aviso: 'A categoria ou a subcategoria está inativa.' }),
    ]);
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h2')?.textContent).toContain('Contas fixas de novembro de 2026');
    const checkboxes = element.querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    expect(checkboxes[0].checked).toBe(true);
    expect(checkboxes[1].checked).toBe(false);
    expect(element.textContent).toContain('A categoria ou a subcategoria está inativa.');
    expect(element.querySelector('.primary-button')?.textContent).toContain('Aprovar (1)');
  });

  it('sends only the selected items with the edited value and date', () => {
    const fixture = criar([previa({}), previa({ serieId: 'serie-2', descricao: 'Água', valorPorParcela: 80 })]);
    const element = fixture.nativeElement as HTMLElement;
    const gerado = vi.fn();
    fixture.componentInstance.gerado.subscribe(gerado);

    const [primeiro, segundo] = Array.from(element.querySelectorAll<HTMLElement>('.conta-fixa-item'));
    digitar(primeiro.querySelector<HTMLInputElement>('input[type="number"]')!, '123.45');
    digitar(primeiro.querySelector<HTMLInputElement>('input[type="date"]')!, '2026-11-15');
    segundo.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    fixture.detectChanges();
    expect(element.textContent).toMatch(/123[.,]45/);

    element.querySelector<HTMLButtonElement>('.primary-button')!.click();
    const request = http.expectOne('/api/saidas/contas-fixas');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      mes: 11,
      ano: 2026,
      itens: [{ serieId: 'serie-1', valorPorParcela: 123.45, data: '2026-11-15' }],
    });
    request.flush({ data: { criadas: [], ignoradas: 0 } });

    expect(gerado).toHaveBeenCalledWith({ criadas: [], ignoradas: 0 });
  });

  it('blocks approval when the date is outside the month', () => {
    const fixture = criar([previa({})]);
    const element = fixture.nativeElement as HTMLElement;

    digitar(element.querySelector<HTMLInputElement>('input[type="date"]')!, '2026-12-01');
    element.querySelector<HTMLButtonElement>('.primary-button')!.click();
    fixture.detectChanges();

    http.expectNone('/api/saidas/contas-fixas');
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Revise "Internet"');
  });

  it('shows the backend error and keeps the dialog open', () => {
    const fixture = criar([previa({})]);
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('.primary-button')!.click();
    http.expectOne('/api/saidas/contas-fixas').flush(
      { message: 'Categoria não encontrada' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Categoria não encontrada');
    expect(element.querySelector<HTMLButtonElement>('.primary-button')!.disabled).toBe(false);
  });

  it('tells the user when every fixed account is already registered', () => {
    const fixture = criar([]);
    const element = fixture.nativeElement as HTMLElement;
    const cancelado = vi.fn();
    fixture.componentInstance.cancelado.subscribe(cancelado);

    expect(element.textContent).toContain('Todas as contas fixas deste mês já foram lançadas');
    expect(element.querySelector('.primary-button')).toBeNull();
    Array.from(element.querySelectorAll<HTMLButtonElement>('.secondary-button'))
      .find((botao) => botao.textContent?.includes('Fechar'))!.click();
    expect(cancelado).toHaveBeenCalled();
  });
});
