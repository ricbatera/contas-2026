import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Pessoa } from '../../core/pessoa.service';
import { Saida } from '../../core/saida.service';
import { vi } from 'vitest';
import { mesReferencia } from '../../shared/data/mes-referencia';
import { PessoaSaidas } from './pessoa-saidas';
import { DadosRelatorioPessoa, RelatorioPessoaService } from './relatorio-pessoa';

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
  let exportar: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    exportar = vi.fn(() => Promise.resolve());
    TestBed.configureTestingModule({
      imports: [PessoaSaidas],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RelatorioPessoaService, useValue: { exportar } },
      ],
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

  it('exports only what the search shows, with the filtered total and the chart', async () => {
    const { fixture, element } = criar([
      saida({}),
      saida({
        id: 'saida-2', descricao: 'Farmácia', quantidadeParcelas: 1, formaPagamento: 'PIX', cartaoNome: null,
        responsabilidades: [{ pessoaId: 'pessoa-1', usuarioId: null, nome: 'Ana', valorPorParcela: 25 }],
        parcelas: [{ id: 'p-f', numero: 1, valor: 25, vencimento: data(0, 3), faturaId: null, vencimentoFatura: null }],
      }),
    ]);
    const busca = element.querySelector<HTMLInputElement>('input[type="search"]')!;
    busca.value = 'mercado';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const botao = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'PDF')!;
    botao.click();
    await fixture.whenStable();

    const [tipo, dados, nome] = exportar.mock.calls[0] as [string, DadosRelatorioPessoa, string];
    const { mes, ano } = mesReferencia();
    expect(tipo).toBe('pdf');
    expect(nome).toBe(`saidas-ana-${ano}-${String(mes).padStart(2, '0')}.pdf`);
    expect(dados.pessoa).toBe('Ana');
    expect(dados.busca).toBe('mercado');
    expect(dados.total).toBe(40);
    expect(dados.itens).toHaveLength(1);
    expect(dados.itens[0]).toMatchObject({
      descricao: 'Mercado',
      detalhe: 'Casa · Alimentação',
      valor: 40,
      valorParcela: 100,
    });
    expect(dados.itens[0].info).toContain('Parcela 1/3 · Cartão de crédito · Nubank');
    expect(dados.progressao).toHaveLength(12);
    // O gráfico, como na tela, não é filtrado pela busca: no 1º mês soma a Farmácia também.
    expect(dados.progressao.slice(0, 4).map((item) => item.total)).toEqual([65, 40, 40, 0]);
  });

  it('shows an error when the file cannot be generated', async () => {
    exportar.mockRejectedValueOnce(new Error('sem canvas'));
    const { fixture, element } = criar([saida({})]);

    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Imagem')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')!.textContent).toContain('Não foi possível gerar a imagem');
    expect(Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Imagem')!.disabled)
      .toBe(false);
  });
});
