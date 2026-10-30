import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Pessoa } from '../../core/pessoa.service';
import { Saida } from '../../core/saida.service';
import { mesReferencia } from '../../shared/data/mes-referencia';
import { PessoaSaidas } from './pessoa-saidas';

const PESSOAS: Pessoa[] = [
  { id: 'pessoa-2', nome: 'Bruno', ativo: true, criadoEm: null },
  { id: 'pessoa-1', nome: 'Ana', ativo: true, criadoEm: null },
];

/** Data no mês de referência deslocado em `offset` meses. */
function data(offset: number, dia: number): string {
  const { mes, ano } = mesReferencia();
  const base = new Date(Date.UTC(ano, mes - 1 + offset, dia));
  return base.toISOString().slice(0, 10);
}

function saida(overrides: Partial<Saida>): Saida {
  return {
    id: 'saida-1',
    descricao: 'Mercado',
    descricaoFatura: null,
    contaFixa: false,
    valorPorParcela: 100,
    valorTotal: 300,
    quantidadeParcelas: 3,
    formaPagamento: 'CARTAO_CREDITO',
    modoResponsabilidade: 'DIVIDIR',
    dataInicial: null,
    primeiroVencimento: data(0, 10),
    cartaoId: 'cartao-1',
    cartaoNome: 'Nubank',
    categoriaId: 'cat-1',
    categoriaNome: 'Casa',
    subcategoriaId: 'sub-1',
    subcategoriaNome: 'Alimentação',
    responsavelPessoaId: null,
    responsabilidades: [
      { pessoaId: 'pessoa-1', usuarioId: null, nome: 'Ana', valorPorParcela: 40 },
      { pessoaId: null, usuarioId: 'user-1', nome: 'Eu', valorPorParcela: 60 },
    ],
    parcelas: [0, 1, 2].map((offset) => ({
      id: `p-${offset}`,
      numero: offset + 1,
      valor: 100,
      vencimento: data(offset, 10),
      faturaId: null,
      vencimentoFatura: null,
    })),
    criadoEm: null,
    ...overrides,
  };
}

describe('PessoaSaidas', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PessoaSaidas],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function criar(saidas: Saida[]): { fixture: ComponentFixture<PessoaSaidas>; element: HTMLElement } {
    const fixture = TestBed.createComponent(PessoaSaidas);
    fixture.componentRef.setInput('pessoas', PESSOAS);
    fixture.detectChanges();
    http.expectOne('/api/saidas').flush({ data: saidas });
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('lists the installments of the first person in the reference month with only her share', () => {
    const { element } = criar([
      saida({}),
      saida({
        id: 'saida-2', descricao: 'Academia', quantidadeParcelas: 1, valorPorParcela: 90, valorTotal: 90,
        formaPagamento: 'PIX', cartaoNome: null,
        responsabilidades: [{ pessoaId: 'pessoa-2', usuarioId: null, nome: 'Bruno', valorPorParcela: 90 }],
        parcelas: [{ id: 'p-a', numero: 1, valor: 90, vencimento: data(0, 5), faturaId: null, vencimentoFatura: null }],
      }),
    ]);

    // As pessoas são ordenadas por nome: Ana vem primeiro.
    expect(element.querySelector<HTMLSelectElement>('select')!.value).toBe('pessoa-1');
    const titulos = Array.from(element.querySelectorAll('.fatura-item h4')).map((h) => h.textContent?.trim());
    expect(titulos).toEqual(['Mercado']);
    expect(element.textContent).toContain('Parcela 1/3');
    expect(element.querySelector('.pessoa-saida-valor')!.textContent).toMatch(/40[.,]00/);
    expect(element.querySelector('.pessoa-saida-valor small')!.textContent).toMatch(/de R\$\s?100[.,]00/);
    expect(element.querySelector('.pessoa-total strong')!.textContent).toMatch(/R\$\s?40,00/);
    expect(element.querySelector('.pessoa-total-nome')!.textContent).toBe('Ana');
    expect(element.querySelector('a[href="/home/saidas/saida-1/editar"]')).not.toBeNull();
  });

  it('switches person and month', () => {
    const { fixture, element } = criar([
      saida({}),
      saida({
        id: 'saida-2', descricao: 'Academia', quantidadeParcelas: 1,
        responsabilidades: [{ pessoaId: 'pessoa-2', usuarioId: null, nome: 'Bruno', valorPorParcela: 90 }],
        parcelas: [{ id: 'p-a', numero: 1, valor: 90, vencimento: data(1, 5), faturaId: null, vencimentoFatura: null }],
      }),
    ]);

    const select = element.querySelector<HTMLSelectElement>('select')!;
    select.value = 'pessoa-2';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(element.textContent).toContain('Nenhuma saída desta pessoa vence em');

    element.querySelector<HTMLButtonElement>('button[aria-label="Próximo mês"]')!.click();
    fixture.detectChanges();
    const titulos = Array.from(element.querySelectorAll('.fatura-item h4')).map((h) => h.textContent?.trim());
    expect(titulos).toEqual(['Academia']);
  });

  it('charts the monthly total of the person from the selected month', () => {
    const { fixture, element } = criar([saida({})]);

    let barras = element.querySelectorAll('.chart-bar');
    expect(barras).toHaveLength(12);
    expect(element.querySelectorAll('.chart-bar rect')).toHaveLength(3);
    expect(barras[0].getAttribute('aria-label')).toMatch(/Ana R\$\s?40,00/);
    expect(barras[3].querySelector('rect')).toBeNull();

    const quantidade = element.querySelector<HTMLSelectElement>('.projection-count select')!;
    quantidade.value = '6';
    quantidade.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    barras = element.querySelectorAll('.chart-bar');
    expect(barras).toHaveLength(6);
  });
});
