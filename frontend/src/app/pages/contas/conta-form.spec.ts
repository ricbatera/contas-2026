import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { LOCALE_ID } from '@angular/core';
import { vi } from 'vitest';
import { ContaForm } from './conta-form';

describe('ContaForm', () => {
  let http: HttpTestingController;
  let routeId: string | null;

  beforeEach(() => {
    registerLocaleData(localePtBr);
    routeId = null;
    TestBed.configureTestingModule({
      imports: [ContaForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (key: string) => (key === 'id' ? routeId : null) } } },
        },
        { provide: LOCALE_ID, useValue: 'pt-BR' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('prevents submission when the initial balance is negative', () => {
    const fixture = TestBed.createComponent(ContaForm);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const balance = element.querySelector<HTMLInputElement>('[formControlName="saldoInicial"]')!;
    balance.value = '-1';
    balance.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();

    expect(element.textContent).toContain('O saldo inicial deve ser maior ou igual a R$ 0,00.');
    http.expectNone('/api/contas-bancarias');
  });

  it('creates an account with zero balance and null optional fields', async () => {
    const fixture = TestBed.createComponent(ContaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    for (const [name, value] of [
      ['nome', 'Conta corrente'],
      ['banco', 'Banco Azul'],
    ]) {
      const input = element.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const creation = http.expectOne('/api/contas-bancarias');
    expect(creation.request.method).toBe('POST');
    expect(creation.request.body).toEqual({
      nome: 'Conta corrente',
      banco: 'Banco Azul',
      tipoConta: 'CORRENTE',
      agencia: null,
      numeroConta: null,
      saldoInicial: 0,
      cor: '#1e6fd0',
      ativo: true,
    });
    creation.flush({
      data: {
        id: 'account-1',
        nome: 'Conta corrente',
        banco: 'Banco Azul',
        tipoConta: 'CORRENTE',
        agencia: null,
        numeroConta: null,
        saldoInicial: 0,
        cor: '#1e6fd0',
        ativo: true,
        criadoEm: null,
      },
    });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/contas']);
  });

  it('loads account data for editing and submits an update', async () => {
    routeId = 'account-1';
    const fixture = TestBed.createComponent(ContaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();

    http.expectOne('/api/contas-bancarias/account-1').flush({
      data: {
        id: 'account-1',
        nome: 'Conta existente',
        banco: 'Banco Azul',
        tipoConta: 'POUPANCA',
        agencia: '0002',
        numeroConta: '9988',
        saldoInicial: 85.25,
        cor: '#abcdef',
        ativo: false,
        criadoEm: '2026-01-01T00:00:00Z',
      },
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector<HTMLInputElement>('[formControlName="nome"]')?.value).toBe(
      'Conta existente',
    );
    expect(element.querySelector<HTMLInputElement>('[formControlName="ativo"]')?.checked).toBe(false);

    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    const update = http.expectOne('/api/contas-bancarias/account-1');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body.saldoInicial).toBe(85.25);
    expect(update.request.body.tipoConta).toBe('POUPANCA');
    expect(update.request.body.ativo).toBe(false);
    update.flush({ data: { id: 'account-1' } });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/contas']);
  });

  it('updates the optional color field when a color is picked', () => {
    const fixture = TestBed.createComponent(ContaForm);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const picker = element.querySelector<HTMLInputElement>('input[type="color"]')!;
    picker.value = '#228844';
    picker.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();

    expect(element.querySelector<HTMLInputElement>('input[type="text"][formControlName="cor"]')!.value).toBe('#228844');
  });
});
