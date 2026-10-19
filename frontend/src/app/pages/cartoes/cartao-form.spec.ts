import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { CartaoForm } from './cartao-form';

describe('CartaoForm', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CartaoForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => null } } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('validates the required fields, four digits, and hexadecimal color', () => {
    const fixture = TestBed.createComponent(CartaoForm);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    http.expectOne('/api/cartoes/principais').flush({ data: [] });

    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = 'Principal';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    const bank = element.querySelector<HTMLInputElement>('[formControlName="banco"]')!;
    bank.value = 'Banco';
    bank.dispatchEvent(new Event('input', { bubbles: true }));
    const digits = element.querySelector<HTMLInputElement>('[formControlName="ultimosDigitos"]')!;
    digits.value = '123';
    digits.dispatchEvent(new Event('input', { bubbles: true }));
    const color = element.querySelector<HTMLInputElement>('input[type="text"][formControlName="cor"]')!;
    color.value = 'blue';
    color.dispatchEvent(new Event('input', { bubbles: true }));

    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();

    expect(element.textContent).toContain('Informe exatamente 4 dígitos numéricos.');
    expect(element.textContent).toContain('Informe uma cor hexadecimal válida');
    http.expectNone('/api/cartoes');
  });

  it('requires choosing a principal for an additional card', () => {
    const fixture = TestBed.createComponent(CartaoForm);
    fixture.detectChanges();
    http.expectOne('/api/cartoes/principais').flush({
      data: [
        {
          id: 'principal-1',
          nome: 'Principal',
          banco: 'Banco',
          bandeira: 'VISA',
          ultimosDigitos: '4321',
          cor: '#1E6FD0',
          criadoEm: '2026-01-01T00:00:00Z',
          tipo: 'PRINCIPAL',
          virtual: false,
          cartaoPrincipalId: null,
          ativo: true,
        },
      ],
    });

    const element = fixture.nativeElement as HTMLElement;
    const type = element.querySelector<HTMLSelectElement>('[formControlName="tipo"]')!;
    type.value = 'ADICIONAL';
    type.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();

    expect(element.textContent).toContain('Associar ao cartão principal');
    const parent = element.querySelector<HTMLSelectElement>('[formControlName="cartaoPrincipalId"]')!;
    expect(parent.options[1].textContent).toContain('Principal');

    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();
    expect(element.textContent).toContain('Selecione um cartão principal para este adicional.');
    http.expectNone('/api/cartoes');
  });

  it('sends an additional virtual card with its selected principal', () => {
    const fixture = TestBed.createComponent(CartaoForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    const principal = {
      id: 'principal-1',
      nome: 'Principal',
      banco: 'Banco',
      bandeira: 'VISA',
      ultimosDigitos: '4321',
      cor: '#1E6FD0',
      criadoEm: '2026-01-01T00:00:00Z',
      tipo: 'PRINCIPAL',
      virtual: false,
      cartaoPrincipalId: null,
      ativo: true,
    };
    http.expectOne('/api/cartoes/principais').flush({ data: [principal] });

    const element = fixture.nativeElement as HTMLElement;
    for (const [name, value] of [
      ['nome', 'Adicional virtual'],
      ['banco', 'Banco'],
      ['ultimosDigitos', '6789'],
    ]) {
      const input = element.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    const type = element.querySelector<HTMLSelectElement>('[formControlName="tipo"]')!;
    type.value = 'ADICIONAL';
    type.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();

    const parent = element.querySelector<HTMLSelectElement>('[formControlName="cartaoPrincipalId"]')!;
    parent.value = 'principal-1';
    parent.dispatchEvent(new Event('change', { bubbles: true }));
    const virtual = element.querySelector<HTMLInputElement>('[formControlName="virtual"]')!;
    virtual.checked = true;
    virtual.dispatchEvent(new Event('change', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const request = http.expectOne('/api/cartoes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      nome: 'Adicional virtual',
      banco: 'Banco',
      bandeira: 'VISA',
      ultimosDigitos: '6789',
      cor: '#1e6fd0',
      tipo: 'ADICIONAL',
      virtual: true,
      cartaoPrincipalId: 'principal-1',
      ativo: true,
      diaFechamento: null,
      diaVencimento: null,
    });
    request.flush({
      data: {
        ...principal,
        id: 'additional-1',
        nome: 'Adicional virtual',
        ultimosDigitos: '6789',
        tipo: 'ADICIONAL',
        virtual: true,
        cartaoPrincipalId: 'principal-1',
      },
    });
    expect(navigate).toHaveBeenCalledWith(['/home/cartoes']);
  });

  it('sends the closing and due days of a principal card', () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CartaoForm);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    http.expectOne('/api/cartoes/principais').flush({ data: [] });

    for (const [name, value] of [
      ['nome', 'Principal'],
      ['banco', 'Banco'],
      ['ultimosDigitos', '1234'],
      ['diaFechamento', '3'],
      ['diaVencimento', '10'],
    ]) {
      const input = element.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const request = http.expectOne('/api/cartoes');
    expect(request.request.body).toMatchObject({ tipo: 'PRINCIPAL', diaFechamento: 3, diaVencimento: 10 });
    request.flush({ data: { id: 'card-1' } });

    const type = element.querySelector<HTMLSelectElement>('[formControlName="tipo"]')!;
    type.value = 'ADICIONAL';
    type.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(element.querySelector('[formControlName="diaFechamento"]')).toBeNull();
  });

  it('updates the hexadecimal field when a color is picked', () => {
    const fixture = TestBed.createComponent(CartaoForm);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    http.expectOne('/api/cartoes/principais').flush({ data: [] });

    const picker = element.querySelector<HTMLInputElement>('input[type="color"]')!;
    picker.value = '#ff8800';
    picker.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();

    expect(element.querySelector<HTMLInputElement>('input[type="text"][formControlName="cor"]')!.value).toBe('#ff8800');
  });
});
