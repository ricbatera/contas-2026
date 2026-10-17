import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CartoesList } from './cartoes-list';

describe('CartoesList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CartoesList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders cards returned by the API', () => {
    const fixture = TestBed.createComponent(CartoesList);
    fixture.detectChanges();
    http.expectOne('/api/cartoes').flush({
      data: [
        {
          id: 'card-1',
          nome: 'Principal',
          banco: 'Banco',
          bandeira: 'VISA',
          ultimosDigitos: '1234',
          cor: '#1E6FD0',
          criadoEm: '2026-01-01T00:00:00Z',
          tipo: 'PRINCIPAL',
          virtual: false,
          cartaoPrincipalId: null,
          ativo: true,
        },
      ],
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Principal');
    expect(element.textContent).toContain('Banco');
    expect(element.textContent).toContain('1234');
    expect(element.querySelector('[aria-label="Editar cartão Principal"]')).toBeTruthy();
  });

  it('shows an empty state when the API returns no cards', () => {
    const fixture = TestBed.createComponent(CartoesList);
    fixture.detectChanges();
    http.expectOne('/api/cartoes').flush({ data: [] });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nenhum cartão cadastrado',
    );
  });
});
