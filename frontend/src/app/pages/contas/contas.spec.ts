import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { registerLocaleData } from '@angular/common';
import localePtBr from '@angular/common/locales/pt';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { ContasList } from './contas-list';

describe('ContasList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    registerLocaleData(localePtBr);
    TestBed.configureTestingModule({
      imports: [ContasList],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'pt-BR' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const accounts = [
    {
      id: 'account-1',
      nome: 'Conta ativa',
      banco: 'Banco Um',
      tipoConta: 'CORRENTE',
      agencia: '0001',
      numeroConta: '1234-5',
      saldoInicial: 1250.5,
      cor: '#1E6FD0',
      ativo: true,
      criadoEm: '2026-01-01T00:00:00Z',
    },
    {
      id: 'account-2',
      nome: 'Carteira antiga',
      banco: 'Dinheiro',
      tipoConta: 'CARTEIRA',
      agencia: null,
      numeroConta: null,
      saldoInicial: 0,
      cor: null,
      ativo: false,
      criadoEm: null,
    },
  ];

  it('renders active and inactive accounts with their balances', () => {
    const fixture = TestBed.createComponent(ContasList);
    fixture.detectChanges();
    http.expectOne('/api/contas-bancarias').flush({ data: accounts });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Conta ativa');
    expect(element.textContent).toContain('Ativa');
    expect(element.textContent).toContain('Inativa');
    expect(element.textContent).toContain('R$');
    expect(element.querySelector('[aria-label="Editar conta Conta ativa"]')).toBeTruthy();
  });

  it('shows an empty state when there are no accounts', () => {
    const fixture = TestBed.createComponent(ContasList);
    fixture.detectChanges();
    http.expectOne('/api/contas-bancarias').flush({ data: [] });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma conta cadastrada');
  });

  it('asks for confirmation and removes an account after successful delete', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(ContasList);
    fixture.detectChanges();
    http.expectOne('/api/contas-bancarias').flush({ data: accounts });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('[aria-label="Excluir conta Conta ativa"]')!.click();
    expect(confirm).toHaveBeenCalledOnce();
    const deletion = http.expectOne('/api/contas-bancarias/account-1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(element.textContent).not.toContain('Conta ativa');
    expect(element.textContent).toContain('Carteira antiga');
    confirm.mockRestore();
  });

  it('does not delete an account when confirmation is declined', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const fixture = TestBed.createComponent(ContasList);
    fixture.detectChanges();
    http.expectOne('/api/contas-bancarias').flush({ data: accounts });
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[aria-label="Excluir conta Conta ativa"]')!
      .click();

    expect(http.match('/api/contas-bancarias/account-1')).toHaveLength(0);
    confirm.mockRestore();
  });
});
