import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { PessoaForm } from './pessoa-form';

describe('PessoaForm', () => {
  let http: HttpTestingController;
  let routeId: string | null;

  beforeEach(() => {
    routeId = null;
    TestBed.configureTestingModule({
      imports: [PessoaForm],
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

  it('creates an active person with a trimmed name', async () => {
    const fixture = TestBed.createComponent(PessoaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = '  Maria Silva  ';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const creation = http.expectOne('/api/pessoas');
    expect(creation.request.method).toBe('POST');
    expect(creation.request.body).toEqual({ nome: 'Maria Silva', ativo: true });
    creation.flush({ data: { id: 'pessoa-1', nome: 'Maria Silva', ativo: true, criadoEm: null } });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/pessoas']);
  });

  it('rejects an empty name before calling the API', () => {
    const fixture = TestBed.createComponent(PessoaForm);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = '   ';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();

    expect(element.textContent).toContain('Informe um nome válido');
    http.expectNone('/api/pessoas');
  });

  it('loads a person and submits an inactive update', async () => {
    routeId = 'pessoa-1';
    const fixture = TestBed.createComponent(PessoaForm);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne('/api/pessoas/pessoa-1').flush({
      data: { id: 'pessoa-1', nome: 'Maria', ativo: true, criadoEm: null },
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const name = element.querySelector<HTMLInputElement>('[formControlName="nome"]')!;
    name.value = 'Maria Souza';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    element.querySelector<HTMLInputElement>('[formControlName="ativo"]')!.click();
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const update = http.expectOne('/api/pessoas/pessoa-1');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ nome: 'Maria Souza', ativo: false });
    update.flush({ data: { id: 'pessoa-1', nome: 'Maria Souza', ativo: false, criadoEm: null } });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/home/pessoas']);
  });
});
