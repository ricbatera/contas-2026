import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ComprovanteRevisao } from './comprovante-revisao';

const COMPROVANTE = {
  id: 'comp-1',
  nomeArquivo: 'foto.jpg',
  tipoMidia: 'image/jpeg',
  tamanhoBytes: 1000,
  status: 'PENDENTE',
  erroLeitura: null,
  avisos: ['Nenhum cartão cadastrado foi identificado pelo final 5555; selecione o cartão.'],
  estabelecimento: 'LOJA DO ZE',
  ultimosDigitosLidos: '5555',
  bandeiraLida: 'VISA',
  provedorModelo: 'gpt-teste',
  modalidade: 'CREDITO',
  descricao: 'LOJA DO ZE',
  dataCompra: '2026-03-20',
  valorTotal: 300,
  quantidadeParcelas: 3,
  valorPorParcela: 100,
  primeiroVencimento: null,
  cartaoId: null,
  cartaoNome: null,
  cartaoUltimosDigitos: null,
  categoriaId: 'cat-1',
  categoriaNome: 'Compras',
  subcategoriaId: 'sub-1',
  subcategoriaNome: 'Lojas',
  saidaId: null,
  criadoEm: '2026-03-20T10:00:00',
};

const CARTOES = [
  { id: 'card-1', nome: 'Principal', ultimosDigitos: '1111', tipo: 'PRINCIPAL', ativo: true,
    cartaoPrincipalId: null, diaFechamento: 15, diaVencimento: 25 },
  { id: 'card-2', nome: 'Adicional', ultimosDigitos: '2222', tipo: 'ADICIONAL', ativo: true,
    cartaoPrincipalId: 'card-1', diaFechamento: null, diaVencimento: null },
];

const CATEGORIAS = [
  { id: 'cat-1', nome: 'Compras', tipo: 'DESPESA', ativo: true,
    subcategorias: [{ id: 'sub-1', nome: 'Lojas', ativo: true }] },
];

describe('ComprovanteRevisao', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ComprovanteRevisao],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'comp-1' } } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    URL.createObjectURL = vi.fn(() => 'blob:imagem');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => http.verify());

  function carregar(comprovante: object = COMPROVANTE) {
    const fixture = TestBed.createComponent(ComprovanteRevisao);
    fixture.detectChanges();
    http.expectOne('/api/comprovantes/comp-1').flush({ data: comprovante });
    http.expectOne('/api/cartoes').flush({ data: CARTOES });
    http.expectOne('/api/categorias').flush({ data: CATEGORIAS });
    http.expectOne('/api/comprovantes/comp-1/imagem').flush(new Blob(['x'], { type: 'image/jpeg' }));
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('shows the photo, the AI warnings, and the per-installment value', () => {
    const { element } = carregar();

    expect(element.querySelector<HTMLImageElement>('.comprovante-imagem img')!.src).toBe('blob:imagem');
    expect(element.textContent).toContain('final 5555');
    expect(element.textContent).toContain('Crédito em 3x de');
    expect(element.textContent).toContain('cartão, vencimento da 1ª parcela');
    const aprovar = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes('Aprovar'))!;
    expect(aprovar.disabled).toBe(true);
  });

  it('computes the due date from the principal card days, then saves and approves', () => {
    const { fixture, element } = carregar();

    const cartao = element.querySelector<HTMLSelectElement>('[formControlName="cartaoId"]')!;
    cartao.value = 'card-2';
    cartao.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(element.querySelector<HTMLInputElement>('[formControlName="primeiroVencimento"]')!.value).toBe('2026-04-25');

    const aprovar = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes('Aprovar'))!;
    expect(aprovar.disabled).toBe(false);
    aprovar.click();

    const put = http.expectOne((req) => req.method === 'PUT' && req.url === '/api/comprovantes/comp-1');
    expect(put.request.body).toMatchObject({
      modalidade: 'CREDITO',
      cartaoId: 'card-2',
      valorTotal: 300,
      quantidadeParcelas: 3,
      primeiroVencimento: '2026-04-25',
      subcategoriaId: 'sub-1',
    });
    put.flush({ data: { ...COMPROVANTE, cartaoId: 'card-2', primeiroVencimento: '2026-04-25' } });
    http.expectOne('/api/comprovantes/comp-1/aprovar')
      .flush({ data: { ...COMPROVANTE, cartaoId: 'card-2', status: 'APROVADO', saidaId: 'saida-1' } });
    fixture.detectChanges();

    expect(element.textContent).toContain('Comprovante aprovado');
    expect(element.querySelector('a[href="/home/saidas/saida-1/editar"]')).not.toBeNull();
    expect(element.querySelector<HTMLSelectElement>('[formControlName="cartaoId"]')!.disabled).toBe(true);
  });

  it('hides installments and due date for debit purchases', () => {
    const { element } = carregar({ ...COMPROVANTE, modalidade: 'DEBITO', quantidadeParcelas: 1 });

    expect(element.querySelector('[formControlName="quantidadeParcelas"]')).toBeNull();
    expect(element.querySelector('[formControlName="primeiroVencimento"]')).toBeNull();
    expect(element.textContent).not.toContain('vencimento da 1ª parcela');
  });
});
