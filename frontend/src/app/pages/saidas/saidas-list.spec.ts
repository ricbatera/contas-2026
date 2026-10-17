import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Saida } from '../../core/saida.service';
import { SaidasList } from './saidas-list';

function dataEsteMes(dia: number): string {
  const hoje = new Date();
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
}

function dataMesOffset(offset: number, dia: number): string {
  const hoje = new Date();
  const data = new Date(hoje.getFullYear(), hoje.getMonth() + offset, dia);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

function dataDiaOffset(offset: number): string {
  const data = new Date();
  data.setDate(data.getDate() + offset);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

function competenciaAtual(): { mes: string; ano: string } {
  const hoje = new Date();
  return {
    mes: String(hoje.getMonth() + 1).padStart(2, '0'),
    ano: String(hoje.getFullYear()),
  };
}

function selecionarCompetencia(element: HTMLElement, data: string): void {
  const [ano, mes] = data.split('-');
  selecionar(element, 'Mês', mes);
  selecionar(element, 'Ano', ano);
}

function saida(overrides: Partial<Saida>): Saida {
  return {
    id: 'expense-1',
    descricao: 'Compra',
    descricaoFatura: null,
    contaFixa: false,
    valorPorParcela: 50,
    valorTotal: 50,
    quantidadeParcelas: 1,
    formaPagamento: 'PIX',
    modoResponsabilidade: 'USUARIO',
    dataInicial: null,
    primeiroVencimento: null,
    cartaoId: null,
    cartaoNome: null,
    categoriaId: 'category-1',
    categoriaNome: 'Alimentação',
    subcategoriaId: 'subcategory-1',
    subcategoriaNome: 'Mercado',
    responsavelPessoaId: null,
    responsabilidades: [{
      pessoaId: null,
      usuarioId: 'user-1',
      nome: 'Ana Silva',
      valorPorParcela: 50,
    }],
    parcelas: [{
      id: 'installment-1',
      numero: 1,
      valor: 50,
      vencimento: dataEsteMes(15),
      faturaId: null,
      vencimentoFatura: null,
    }],
    criadoEm: null,
    ...overrides,
  };
}

function selecionar(element: HTMLElement, rotulo: string, valor: string): void {
  const label = Array.from(element.querySelectorAll('label'))
    .find((item) => item.firstChild?.textContent?.trim() === rotulo);
  const control = label?.querySelector<HTMLSelectElement>('select');
  if (!control) {
    throw new Error(`Filtro "${rotulo}" não encontrado.`);
  }
  control.value = valor;
  control.dispatchEvent(new Event('change', { bubbles: true }));
}

function selecionarValor(element: HTMLElement, rotulo: string): string {
  const label = Array.from(element.querySelectorAll('label'))
    .find((item) => item.firstChild?.textContent?.trim() === rotulo);
  const control = label?.querySelector<HTMLSelectElement>('select');
  if (!control) {
    throw new Error(`Filtro "${rotulo}" não encontrado.`);
  }
  return control.value;
}

function preencher(element: HTMLElement, seletor: string, valor: string): void {
  const input = element.querySelector<HTMLInputElement>(seletor);
  if (!input) {
    throw new Error(`Campo "${seletor}" não encontrado.`);
  }
  input.value = valor;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

function preencherRotulado(element: HTMLElement, rotulo: string, valor: string): void {
  const label = Array.from(element.querySelectorAll('label'))
    .find((item) => item.firstChild?.textContent?.trim() === rotulo);
  const input = label?.querySelector<HTMLInputElement>('input');
  if (!input) {
    throw new Error(`Campo "${rotulo}" não encontrado.`);
  }
  input.value = valor;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

describe('SaidasList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SaidasList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists expenses, amounts, payment and responsibility', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [{
        id: 'expense-1',
        descricao: 'Mercado',
        descricaoFatura: null,
        contaFixa: true,
        valorPorParcela: 50,
        valorTotal: 100,
        quantidadeParcelas: 2,
        formaPagamento: 'PIX',
        modoResponsabilidade: 'USUARIO',
        dataInicial: null,
        primeiroVencimento: null,
        cartaoId: null,
        cartaoNome: null,
        categoriaId: 'category-1',
        categoriaNome: 'Alimentação',
        subcategoriaId: 'subcategory-1',
        subcategoriaNome: 'Mercado',
        responsavelPessoaId: null,
        responsabilidades: [{ pessoaId: null, usuarioId: 'user-1', nome: 'Ana Silva', valorPorParcela: 50 }],
        parcelas: [{
          id: 'installment-1',
          numero: 1,
          valor: 50,
          vencimento: dataEsteMes(15),
          faturaId: null,
          vencimentoFatura: null,
        }],
        criadoEm: null,
      }],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Mercado');
    expect(element.textContent).toContain('Alimentação · Mercado');
    expect(element.textContent).toContain('Pix');
    expect(element.textContent).toContain('Ana Silva');
    expect(element.textContent).toContain('Conta fixa');
    expect(element.textContent).toContain('Nova saída');
    expect(element.querySelector('.saida-installment-highlight')?.textContent).toContain('1/2');
    expect(element.textContent).toContain('Total da compra:');
    expect(element.textContent).not.toContain('2x');
  });

  it('initially shows only expenses due this month and pre-fills the current month and year', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    const competencia = competenciaAtual();
    const mesAnterior = new Date();
    mesAnterior.setDate(0);
    const dataMesAnterior =
      `${mesAnterior.getFullYear()}-${String(mesAnterior.getMonth() + 1).padStart(2, '0')}-${String(mesAnterior.getDate()).padStart(2, '0')}`;
    http.expectOne('/api/saidas').flush({
      data: [
        saida({ id: 'due-this-month', descricao: 'Vence este mês' }),
        saida({
          id: 'due-last-month',
          descricao: 'Venceu mês passado',
          parcelas: [{
            id: 'last-month-installment',
            numero: 1,
            valor: 50,
            vencimento: dataMesAnterior,
            faturaId: null,
            vencimentoFatura: null,
          }],
        }),
      ],
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Vence este mês');
    expect(element.textContent).not.toContain('Venceu mês passado');
    expect(element.textContent).toContain('1 parcela(s) em 1 de 2 saída(s)');
    expect(element.querySelector('.saida-visible-total')?.textContent).toContain('50.00');

    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    expect(selecionarValor(element, 'Mês')).toBe(competencia.mes);
    expect(selecionarValor(element, 'Ano')).toBe(competencia.ano);
    expect(Array.from(element.querySelectorAll('label'))
      .find((label) => label.firstChild?.textContent?.trim() === 'Ano')
      ?.querySelector('select')?.options.length).toBeGreaterThan(0);
  });

  it('confirms and deletes an expense', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [{
        id: 'expense-1',
        descricao: 'Mercado',
        descricaoFatura: null,
        contaFixa: false,
        valorPorParcela: 50,
        valorTotal: 50,
        quantidadeParcelas: 1,
        formaPagamento: 'PIX',
        modoResponsabilidade: 'USUARIO',
        dataInicial: '2026-11-15',
        primeiroVencimento: null,
        cartaoId: null,
        cartaoNome: null,
        categoriaId: 'category-1',
        categoriaNome: 'Alimentação',
        subcategoriaId: 'subcategory-1',
        subcategoriaNome: 'Mercado',
        responsavelPessoaId: null,
        responsabilidades: [],
        parcelas: [{
          id: 'installment-1',
          numero: 1,
          valor: 50,
          vencimento: dataEsteMes(15),
          faturaId: null,
          vencimentoFatura: null,
        }],
        criadoEm: null,
      }],
    });
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.delete-button')!.click();
    expect(confirm).toHaveBeenCalledOnce();
    const deletion = http.expectOne('/api/saidas/expense-1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma saída cadastrada');
    confirm.mockRestore();
  });

  it('combines description, payment, category, card, person, fixed, due-date and installment-value filters', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [
        saida({
          id: 'computer',
          descricao: 'Computador',
          descricaoFatura: 'Notebook novo',
          formaPagamento: 'CARTAO_CREDITO',
          contaFixa: false,
          valorPorParcela: 500,
          cartaoId: 'card-1',
          cartaoNome: 'Principal',
          categoriaId: 'electronics',
          categoriaNome: 'Eletrônicos',
          subcategoriaId: 'computers',
          subcategoriaNome: 'Computadores',
          responsabilidades: [{
            pessoaId: 'person-1',
            usuarioId: null,
            nome: 'Maria',
            valorPorParcela: 500,
          }],
          parcelas: [{
            id: 'computer-installment',
            numero: 1,
            valor: 500,
            vencimento: '2026-12-15',
            faturaId: 'invoice-1',
            vencimentoFatura: '2026-12-15',
          }],
        }),
        saida({
          id: 'rent',
          descricao: 'Aluguel',
          formaPagamento: 'PIX',
          contaFixa: true,
          valorPorParcela: 1200,
          valorTotal: 3600,
          quantidadeParcelas: 3,
          parcelas: [
            {
              id: 'rent-1',
              numero: 1,
              valor: 1200,
              vencimento: '2026-11-15',
              faturaId: null,
              vencimentoFatura: null,
            },
            {
              id: 'rent-2',
              numero: 2,
              valor: 1200,
              vencimento: '2026-12-15',
              faturaId: null,
              vencimentoFatura: null,
            },
          ],
        }),
        saida({ id: 'market', descricao: 'Mercado' }),
      ],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();

    preencher(element, 'input[type="search"]', 'NOTEBOOK');
    selecionar(element, 'Forma de pagamento', 'CARTAO_CREDITO');
    selecionar(element, 'Conta fixa', 'NAO');
    selecionar(element, 'Categoria', 'electronics');
    selecionar(element, 'Subcategoria', 'computers');
    selecionar(element, 'Cartão', 'card-1');
    selecionar(element, 'Responsável', 'person-1');
    selecionarCompetencia(element, '2026-12-15');
    preencherRotulado(element, 'Mínimo', '500');
    preencherRotulado(element, 'Máximo', '500');
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions .primary-button')!.click();
    fixture.detectChanges();

    expect(element.textContent).toContain('Computador');
    expect(element.textContent).not.toContain('Aluguel');
    expect(element.textContent).not.toContain('Mercado');
    expect(element.textContent).toContain('1 parcela(s) em 1 de 3 saída(s)');
  });

  it('shows and totals the selected person share for the selected month', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [saida({
        id: 'split-expense',
        descricao: 'Compra dividida',
        valorPorParcela: 200,
        valorTotal: 400,
        quantidadeParcelas: 2,
        modoResponsabilidade: 'DIVIDIR',
        responsabilidades: [
          { pessoaId: 'person-1', usuarioId: null, nome: 'Maria', valorPorParcela: 100 },
          { pessoaId: 'person-2', usuarioId: null, nome: 'João', valorPorParcela: 100 },
        ],
        parcelas: [
          { id: 'split-1', numero: 1, valor: 200, vencimento: dataEsteMes(15), faturaId: null, vencimentoFatura: null },
          { id: 'split-2', numero: 2, valor: 200, vencimento: dataMesOffset(1, 15), faturaId: null, vencimentoFatura: null },
        ],
      })],
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const legendaResponsaveis = element.querySelector('.saida-meta span:nth-child(3)')?.textContent ?? '';
    expect(legendaResponsaveis).toContain('Maria');
    expect(legendaResponsaveis).toContain('João');
    expect(legendaResponsaveis.match(/100\.00/g)).toHaveLength(2);

    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    selecionar(element, 'Responsável', 'person-1');
    selecionarCompetencia(element, dataMesOffset(0, 1));
    preencherRotulado(element, 'Mínimo', '100');
    preencherRotulado(element, 'Máximo', '100');
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions .primary-button')!.click();
    fixture.detectChanges();

    const cards = Array.from(element.querySelectorAll<HTMLElement>('.saida-card'));
    expect(cards).toHaveLength(1);
    expect(cards[0].querySelector('.saida-installment-highlight')?.textContent).toContain('100.00');
    expect(cards[0].textContent).toContain('Total da compra:');
    expect(cards[0].textContent).toContain('200.00');
    expect(element.querySelector('.saida-visible-total')?.textContent).toContain('100.00');
  });

  it('matches the selected month and filters by per-installment value', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [
        saida({
          id: 'fixed-rent',
          descricao: 'Aluguel',
          contaFixa: true,
          valorPorParcela: 1000,
          valorTotal: 3000,
          quantidadeParcelas: 3,
          parcelas: [
            { id: 'rent-1', numero: 1, valor: 1000, vencimento: '2026-11-15', faturaId: null, vencimentoFatura: null },
            { id: 'rent-2', numero: 2, valor: 1000, vencimento: '2026-12-15', faturaId: null, vencimentoFatura: null },
            { id: 'rent-3', numero: 3, valor: 1000, vencimento: '2027-01-15', faturaId: null, vencimentoFatura: null },
          ],
        }),
        saida({
          id: 'variable-market',
          descricao: 'Compra variável',
          valorPorParcela: 80,
          parcelas: [{
            id: 'market-1',
            numero: 1,
            valor: 80,
            vencimento: '2026-12-15',
            faturaId: null,
            vencimentoFatura: null,
          }],
        }),
      ],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    selecionar(element, 'Conta fixa', 'SIM');
    selecionarCompetencia(element, '2026-12-15');
    preencherRotulado(element, 'Mínimo', '900');
    preencherRotulado(element, 'Máximo', '1100');
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions .primary-button')!.click();
    fixture.detectChanges();

    expect(element.textContent).toContain('Aluguel');
    expect(element.textContent).not.toContain('Compra variável');
    expect(element.textContent).toContain('1 parcela(s) em 1 de 2 saída(s)');
  });

  it('creates a card per installment due in the selected month and keeps actions on the expense', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [saida({
        id: 'installment-purchase',
        descricao: 'Colchão Luana',
        valorPorParcela: 420.99,
        valorTotal: 5051.88,
        quantidadeParcelas: 12,
        parcelas: Array.from({ length: 12 }, (_, index) => ({
          id: `installment-${index + 1}`,
          numero: index + 1,
          valor: 420.99,
          vencimento: index === 1
            ? dataMesOffset(1, 10)
            : index === 2
              ? dataMesOffset(1, 20)
              : dataMesOffset(index, 15),
          faturaId: null,
          vencimentoFatura: null,
        })),
      })],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    selecionarCompetencia(element, dataMesOffset(1, 1));
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions .primary-button')!.click();
    fixture.detectChanges();

    const cards = Array.from(element.querySelectorAll<HTMLElement>('.saida-card'));
    expect(cards).toHaveLength(2);
    expect(cards.map((card) => card.querySelector('.saida-installment-highlight')?.textContent?.trim())).toEqual([
      expect.stringContaining('2/12'),
      expect.stringContaining('3/12'),
    ]);
    const vencimentos = cards.map((card) =>
      Array.from(card.querySelectorAll('.saida-meta span'))
        .find((span) => span.textContent?.includes('Vencimento da parcela'))
        ?.textContent?.trim(),
    );
    expect(vencimentos).toEqual([
      `Vencimento da parcela: 10/${dataMesOffset(1, 10).slice(5, 7)}/${dataMesOffset(1, 10).slice(0, 4)}`,
      `Vencimento da parcela: 20/${dataMesOffset(1, 20).slice(5, 7)}/${dataMesOffset(1, 20).slice(0, 4)}`,
    ]);
    expect(cards[0].querySelector('.saida-installment-highlight')?.textContent).toContain('420.99');
    expect(cards[1].querySelector('.saida-installment-highlight')?.textContent).toContain('420.99');
    expect(element.textContent).toContain('Total da compra:');
    expect(element.textContent).toContain('5,051.88');
    expect(element.textContent).toContain('2 parcela(s) em 1 de 1 saída(s)');
    expect(element.querySelector('.saida-visible-total')?.textContent).toContain('841.98');

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    cards[1].querySelector<HTMLButtonElement>('.delete-button')!.click();
    expect(confirm).toHaveBeenCalledOnce();
    const deletion = http.expectOne('/api/saidas/installment-purchase');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    fixture.detectChanges();
    expect(element.querySelectorAll('.saida-card')).toHaveLength(0);
    confirm.mockRestore();
  });

  it('shows only installments due in the current month', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [
        saida({
          id: 'future-installments',
          descricao: 'Compra futura',
          quantidadeParcelas: 3,
          parcelas: [
            { id: 'future-1', numero: 1, valor: 100, vencimento: dataDiaOffset(1), faturaId: null, vencimentoFatura: null },
            { id: 'future-2', numero: 2, valor: 100, vencimento: dataDiaOffset(31), faturaId: null, vencimentoFatura: null },
            { id: 'future-3', numero: 3, valor: 100, vencimento: dataDiaOffset(61), faturaId: null, vencimentoFatura: null },
          ],
        }),
        saida({
          id: 'past-installments',
          descricao: 'Compra vencida',
          quantidadeParcelas: 2,
          parcelas: [
            { id: 'past-1', numero: 1, valor: 80, vencimento: dataDiaOffset(-60), faturaId: null, vencimentoFatura: null },
            { id: 'past-2', numero: 2, valor: 80, vencimento: dataEsteMes(1), faturaId: null, vencimentoFatura: null },
          ],
        }),
      ],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const cards = Array.from(element.querySelectorAll<HTMLElement>('.saida-card'));
    const futura = cards.find((card) => card.textContent?.includes('Compra futura'));
    const vencida = cards.find((card) => card.textContent?.includes('Compra vencida'));
    expect(futura?.querySelector('.saida-installment-highlight')?.textContent).toContain('1/3');
    expect(vencida?.querySelector('.saida-installment-highlight')?.textContent).toContain('2/2');
    expect(cards).toHaveLength(2);
  });

  it('keeps applied results when cancelled and clears all filters when requested', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({
      data: [
        saida({ id: 'pix', descricao: 'Pix' }),
        saida({ id: 'boleto', descricao: 'Boleto', formaPagamento: 'BOLETO' }),
      ],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    selecionar(element, 'Forma de pagamento', 'PIX');
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions .primary-button')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Pix');
    expect(element.textContent).not.toContain('Boleto');

    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    selecionar(element, 'Forma de pagamento', 'BOLETO');
    Array.from(element.querySelectorAll<HTMLButtonElement>('.filter-dialog-actions button'))
      .find((button) => button.textContent?.trim() === 'Cancelar')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Pix');
    expect(element.textContent).not.toContain('Boleto');

    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions > .secondary-button')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Pix');
    expect(element.textContent).toContain('Boleto');
    expect(element.textContent).toContain('2 parcela(s) em 2 de 2 saída(s)');
    expect(selecionarValor(element, 'Mês')).toBe(competenciaAtual().mes);
    expect(selecionarValor(element, 'Ano')).toBe(competenciaAtual().ano);
  });

  it('validates value bounds and shows an empty result state for another competence', () => {
    const fixture = TestBed.createComponent(SaidasList);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({ data: [saida({ id: 'one', descricao: 'Única saída' })] });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('.filter-open-button')!.click();
    fixture.detectChanges();
    preencherRotulado(element, 'Mínimo', '100');
    preencherRotulado(element, 'Máximo', '10');
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions .primary-button')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('O valor mínimo não pode ser maior que o valor máximo.');

    preencherRotulado(element, 'Mínimo', '');
    preencherRotulado(element, 'Máximo', '');
    selecionar(element, 'Conta fixa', 'SIM');
    const competencia = competenciaAtual();
    selecionar(element, 'Mês', String(Number(competencia.mes) % 12 + 1).padStart(2, '0'));
    element.querySelector<HTMLButtonElement>('.filter-dialog-actions .primary-button')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Nenhuma saída corresponde aos filtros');
    expect(element.querySelector('.saida-visible-total')?.textContent).toContain('0.00');
  });
});
