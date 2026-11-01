import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Saida } from '../../core/saida.service';
import { ExcluirSaida } from './excluir-saida';

function dataMesOffset(offset: number, dia: number): string {
  const hoje = new Date();
  const data = new Date(hoje.getFullYear(), hoje.getMonth() + offset, dia);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

/** Parcelas mensais, a 1ª vencendo `primeiroOffset` meses a partir de hoje (dia 1, para ser passado ou futuro sem ambiguidade). */
function saida(quantidade: number, primeiroOffset: number): Saida {
  return {
    id: 'assinatura',
    descricao: 'Streaming',
    descricaoFatura: null,
    contaFixa: false,
    valorPorParcela: 30,
    valorTotal: 30 * quantidade,
    quantidadeParcelas: quantidade,
    formaPagamento: 'CARTAO_CREDITO',
    modoResponsabilidade: 'USUARIO',
    dataInicial: null,
    primeiroVencimento: dataMesOffset(primeiroOffset, 1),
    cartaoId: 'card-1',
    cartaoNome: 'Principal',
    categoriaId: 'category-1',
    categoriaNome: 'Lazer',
    subcategoriaId: 'subcategory-1',
    subcategoriaNome: 'Streaming',
    responsavelPessoaId: null,
    responsabilidades: [],
    parcelas: Array.from({ length: quantidade }, (_, index) => ({
      id: `parcela-${index + 1}`,
      numero: index + 1,
      valor: 30,
      vencimento: dataMesOffset(primeiroOffset + index, 1),
      faturaId: `fatura-${index + 1}`,
      vencimentoFatura: dataMesOffset(primeiroOffset + index, 1),
    })),
    criadoEm: null,
  };
}

describe('ExcluirSaida', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ExcluirSaida],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function criar(valor: Saida): ComponentFixture<ExcluirSaida> {
    const fixture = TestBed.createComponent(ExcluirSaida);
    fixture.componentRef.setInput('saida', valor);
    fixture.detectChanges();
    return fixture;
  }

  function selectParcela(fixture: ComponentFixture<ExcluirSaida>): HTMLSelectElement {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('.excluir-saida-futuras select')!;
  }

  function radio(fixture: ComponentFixture<ExcluirSaida>, valor: 'TODAS' | 'FUTURAS'): HTMLInputElement {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`input[value="${valor}"]`)!;
  }

  it('preselects the first installment due after today and summarizes what is deleted', () => {
    // Parcelas 1 e 2 já venceram (2 meses atrás e mês passado); a 3ª vence no dia 1 do mês corrente,
    // que já passou ou é hoje; a 4ª é a primeira futura.
    const fixture = criar(saida(6, -2));
    const select = selectParcela(fixture);
    expect(select.value).toBe('4');
    expect(Array.from(select.options).map((option) => option.value)).toEqual(['2', '3', '4', '5', '6']);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ficam 3 parcela(s). Serão excluídas 3');
  });

  it('defaults to the last installment when all of them are past due', () => {
    const fixture = criar(saida(3, -5));
    expect(selectParcela(fixture).value).toBe('3');
  });

  it('deletes future installments from the chosen one', () => {
    const fixture = criar(saida(6, 1));
    const atualizada = vi.fn();
    fixture.componentInstance.atualizada.subscribe(atualizada);
    const select = selectParcela(fixture);
    expect(select.value).toBe('2');
    select.value = '5';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ficam 4 parcela(s). Serão excluídas 2');

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.excluir-saida-confirmar')!.click();
    const request = http.expectOne((req) => req.url === '/api/saidas/assinatura/parcelas');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.params.get('aPartirDe')).toBe('5');
    const resposta = saida(4, 1);
    request.flush({ data: resposta });
    expect(atualizada).toHaveBeenCalledWith(resposta);
  });

  it('deletes the whole expense when "all installments" is chosen', () => {
    const fixture = criar(saida(6, 1));
    const excluida = vi.fn();
    fixture.componentInstance.excluida.subscribe(excluida);
    radio(fixture, 'TODAS').click();
    fixture.detectChanges();
    expect(selectParcela(fixture)).toBeNull();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.excluir-saida-confirmar')!.click();
    const request = http.expectOne('/api/saidas/assinatura');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    expect(excluida).toHaveBeenCalledWith('assinatura');
  });

  it('shows the API error inside the dialog', () => {
    const fixture = criar(saida(6, 1));
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.excluir-saida-confirmar')!.click();
    http.expectOne((req) => req.url === '/api/saidas/assinatura/parcelas')
      .flush({ message: 'Informe uma parcela entre 2 e 6.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Informe uma parcela entre 2 e 6.');
    expect(element.querySelector<HTMLButtonElement>('.excluir-saida-confirmar')!.disabled).toBe(false);
  });
});
