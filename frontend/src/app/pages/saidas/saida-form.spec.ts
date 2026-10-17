import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { SaidaForm } from './saida-form';

describe('SaidaForm', () => {
  let http: HttpTestingController;
  let routeId: string | null;

  beforeEach(() => {
    routeId = null;
    TestBed.configureTestingModule({
      imports: [SaidaForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (key: string) => (key === 'id' ? routeId : null) } } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function flushDependencies(): void {
    http.expectOne('/api/cartoes').flush({
      data: [{
        id: 'card-1',
        nome: 'Principal',
        banco: 'Banco',
        bandeira: 'VISA',
        ultimosDigitos: '1234',
        cor: '#123456',
        criadoEm: null,
        tipo: 'PRINCIPAL',
        virtual: false,
        cartaoPrincipalId: null,
        ativo: true,
      }],
    });
    http.expectOne('/api/pessoas').flush({
      data: [{ id: 'person-1', nome: 'Maria', ativo: true, criadoEm: null }],
    });
    http.expectOne('/api/categorias').flush({
      data: [{
        id: 'category-1',
        nome: 'Moradia',
        tipo: 'DESPESA',
        ativo: true,
        padrao: true,
        criadoEm: null,
        subcategorias: [{
          id: 'subcategory-1',
          nome: 'Aluguel',
          ativo: true,
          padrao: true,
          categoriaId: 'category-1',
          categoriaNome: 'Moradia',
          criadoEm: null,
        }],
      }],
    });
    http.expectOne('/api/usuarios/me').flush({
      data: { id: 'user-1', nome: 'Ana', sobrenome: 'Silva' },
    });
  }

  it('loads default expense category and submits a credit-card purchase', async () => {
    const fixture = TestBed.createComponent(SaidaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    flushDependencies();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Aluguel · Padrão');
    expect(element.textContent).toContain('Eu (Ana)');
    expect(element.querySelector<HTMLInputElement>('[formControlName="contaFixa"]')?.checked).toBe(false);
    expect(element.querySelector<HTMLSelectElement>('[formControlName="categoriaId"]')?.value)
      .toBe('category-1');
    expect(element.querySelector<HTMLSelectElement>('[formControlName="subcategoriaId"]')?.value)
      .toBe('subcategory-1');

    const description = element.querySelector<HTMLInputElement>('[formControlName="descricao"]')!;
    description.value = 'Aluguel';
    description.dispatchEvent(new Event('input', { bubbles: true }));
    const amount = element.querySelector<HTMLInputElement>('[formControlName="valorPorParcela"]')!;
    amount.value = '1200';
    amount.dispatchEvent(new Event('input', { bubbles: true }));
    const installments = element.querySelector<HTMLSelectElement>('[formControlName="quantidadeParcelas"]')!;
    installments.value = '60';
    installments.dispatchEvent(new Event('change', { bubbles: true }));
    const card = element.querySelector<HTMLSelectElement>('[formControlName="cartaoId"]')!;
    card.value = 'card-1';
    card.dispatchEvent(new Event('change', { bubbles: true }));
    const due = element.querySelector<HTMLInputElement>('[formControlName="primeiroVencimento"]')!;
    due.value = '2026-11-15';
    due.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const request = http.expectOne('/api/saidas');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      descricao: 'Aluguel',
      descricaoFatura: null,
      contaFixa: false,
      valorPorParcela: 1200,
      quantidadeParcelas: 60,
      formaPagamento: 'CARTAO_CREDITO',
      dataInicial: null,
      primeiroVencimento: '2026-11-15',
      cartaoId: 'card-1',
      categoriaId: 'category-1',
      subcategoriaId: 'subcategory-1',
      modoResponsabilidade: 'USUARIO',
      responsavelPessoaId: null,
      partes: [],
    });
    request.flush({ data: {} });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/saidas']);
  });

  it('loads the saved fixed-expense flag when editing', () => {
    routeId = 'expense-1';
    const fixture = TestBed.createComponent(SaidaForm);
    fixture.detectChanges();
    flushDependencies();
    http.expectOne('/api/saidas/expense-1').flush({
      data: {
        id: 'expense-1',
        descricao: 'Aluguel',
        descricaoFatura: null,
        contaFixa: false,
        valorPorParcela: 1200,
        valorTotal: 1200,
        quantidadeParcelas: 1,
        formaPagamento: 'PIX',
        modoResponsabilidade: 'USUARIO',
        dataInicial: '2026-10-15',
        primeiroVencimento: null,
        cartaoId: null,
        cartaoNome: null,
        categoriaId: 'category-1',
        categoriaNome: 'Moradia',
        subcategoriaId: 'subcategory-1',
        subcategoriaNome: 'Aluguel',
        responsavelPessoaId: null,
        responsabilidades: [],
        parcelas: [],
        criadoEm: null,
      },
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement)
      .querySelector<HTMLInputElement>('[formControlName="contaFixa"]')?.checked).toBe(false);
  });

  it('shows monthly non-credit date and calculates the remaining split', () => {
    const fixture = TestBed.createComponent(SaidaForm);
    fixture.detectChanges();
    flushDependencies();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const payment = element.querySelector<HTMLInputElement>(
      '[formControlName="formaPagamento"][value="OUTRO"]',
    )!;
    payment.click();
    payment.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    const method = element.querySelector<HTMLSelectElement>('[formControlName="pagamentoAlternativo"]')!;
    method.value = 'PIX';
    method.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(element.querySelector('[formControlName="dataInicial"]')).not.toBeNull();
    expect(element.querySelector('[formControlName="cartaoId"]')).toBeNull();

    const amount = element.querySelector<HTMLInputElement>('[formControlName="valorPorParcela"]')!;
    amount.value = '100';
    amount.dispatchEvent(new Event('input', { bubbles: true }));
    const mode = element.querySelector<HTMLSelectElement>('[formControlName="modoResponsabilidade"]')!;
    mode.value = 'DIVIDIR';
    mode.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    const person = element.querySelector<HTMLSelectElement>('[formArrayName="partes"] select')!;
    person.value = 'person-1';
    person.dispatchEvent(new Event('change', { bubbles: true }));
    const share = element.querySelector<HTMLInputElement>('[formArrayName="partes"] input')!;
    share.value = '35';
    share.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();

    expect(element.textContent).toContain('Minha parte:');
    expect(element.textContent).toMatch(/Minha parte:\s*R\$\s*65[,.]00/);
    expect(element.textContent).toContain('As próximas parcelas serão programadas mensalmente');
  });

  describe('cadastro rápido', () => {
    function abrirFormulario() {
      const fixture = TestBed.createComponent(SaidaForm);
      fixture.detectChanges();
      flushDependencies();
      fixture.detectChanges();
      const element = fixture.nativeElement as HTMLElement;
      const description = element.querySelector<HTMLInputElement>('[formControlName="descricao"]')!;
      description.value = 'Mercado';
      description.dispatchEvent(new Event('input', { bubbles: true }));
      return { fixture, element };
    }

    function clicar(element: HTMLElement, rotulo: string): void {
      element.querySelector<HTMLButtonElement>(`button[aria-label="${rotulo}"]`)!.click();
    }

    function preencherDialogo(element: HTMLElement, controle: string, valor: string): void {
      const campo = element.querySelector<HTMLInputElement>(`[role="dialog"] [formControlName="${controle}"]`)!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function enviarDialogo(element: HTMLElement): void {
      element.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(
        new Event('submit', { bubbles: true, cancelable: true }),
      );
    }

    function valor(element: HTMLElement, controle: string): string {
      return element.querySelector<HTMLInputElement | HTMLSelectElement>(`[formControlName="${controle}"]`)!.value;
    }

    it('creates a card without leaving the form and selects it', () => {
      const { fixture, element } = abrirFormulario();
      clicar(element, 'Cadastrar novo cartão');
      fixture.detectChanges();
      http.expectOne('/api/cartoes/principais').flush({ data: [] });
      preencherDialogo(element, 'nome', 'Viagem');
      preencherDialogo(element, 'banco', 'Banco 2');
      preencherDialogo(element, 'ultimosDigitos', '5555');
      enviarDialogo(element);
      http.expectOne('/api/cartoes').flush({
        data: {
          id: 'card-2',
          nome: 'Viagem',
          banco: 'Banco 2',
          bandeira: 'VISA',
          ultimosDigitos: '5555',
          cor: '#1e6fd0',
          criadoEm: null,
          tipo: 'PRINCIPAL',
          virtual: false,
          cartaoPrincipalId: null,
          ativo: true,
        },
      });
      fixture.detectChanges();

      expect(element.querySelector('[role="dialog"]')).toBeNull();
      expect(valor(element, 'cartaoId')).toBe('card-2');
      expect(valor(element, 'descricao')).toBe('Mercado');
    });

    it('creates a category with its subcategory and selects both', () => {
      const { fixture, element } = abrirFormulario();
      clicar(element, 'Cadastrar nova categoria');
      fixture.detectChanges();
      preencherDialogo(element, 'nome', 'Pets');
      preencherDialogo(element, 'subcategoria', 'Ração');
      enviarDialogo(element);
      http.expectOne('/api/categorias').flush({
        data: { id: 'category-2', nome: 'Pets', tipo: 'DESPESA', ativo: true, padrao: false, criadoEm: null, subcategorias: [] },
      });
      http.expectOne('/api/categorias/category-2/subcategorias').flush({
        data: { id: 'sub-2', nome: 'Ração', ativo: true, padrao: true, categoriaId: 'category-2', categoriaNome: 'Pets', criadoEm: null },
      });
      fixture.detectChanges();

      expect(valor(element, 'categoriaId')).toBe('category-2');
      expect(valor(element, 'subcategoriaId')).toBe('sub-2');
      expect(valor(element, 'descricao')).toBe('Mercado');
    });

    it('creates a subcategory in the selected category and selects it', () => {
      const { fixture, element } = abrirFormulario();
      clicar(element, 'Cadastrar nova subcategoria');
      fixture.detectChanges();
      expect(element.querySelector('[role="dialog"]')?.textContent).toContain('Moradia');
      preencherDialogo(element, 'nome', 'Condomínio');
      enviarDialogo(element);
      http.expectOne('/api/categorias/category-1/subcategorias').flush({
        data: { id: 'sub-2', nome: 'Condomínio', ativo: true, padrao: false, categoriaId: 'category-1', categoriaNome: 'Moradia', criadoEm: null },
      });
      fixture.detectChanges();

      expect(valor(element, 'categoriaId')).toBe('category-1');
      expect(valor(element, 'subcategoriaId')).toBe('sub-2');
      expect(element.textContent).toContain('Aluguel · Padrão');
    });

    it('creates a person for a split row and selects it in that row', () => {
      const { fixture, element } = abrirFormulario();
      const mode = element.querySelector<HTMLSelectElement>('[formControlName="modoResponsabilidade"]')!;
      mode.value = 'DIVIDIR';
      mode.dispatchEvent(new Event('change', { bubbles: true }));
      fixture.detectChanges();
      clicar(element, 'Cadastrar nova pessoa');
      fixture.detectChanges();
      preencherDialogo(element, 'nome', 'João');
      enviarDialogo(element);
      http.expectOne('/api/pessoas').flush({ data: { id: 'person-2', nome: 'João', ativo: true, criadoEm: null } });
      fixture.detectChanges();

      expect(element.querySelector<HTMLSelectElement>('[formArrayName="partes"] select')!.value).toBe('person-2');
    });

    it('closes the dialog on cancel keeping the form untouched', () => {
      const { fixture, element } = abrirFormulario();
      clicar(element, 'Cadastrar nova categoria');
      fixture.detectChanges();
      Array.from(element.querySelectorAll<HTMLButtonElement>('[role="dialog"] button'))
        .find((button) => button.textContent?.trim() === 'Cancelar')!.click();
      fixture.detectChanges();

      expect(element.querySelector('[role="dialog"]')).toBeNull();
      expect(valor(element, 'categoriaId')).toBe('category-1');
      expect(valor(element, 'descricao')).toBe('Mercado');
    });
  });
});
