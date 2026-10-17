import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Home } from './home';

describe('Home', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the profile and shows all navigation sections', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Ana Silva');
    for (const section of ['Dashboard', 'Fatura', 'Buckets', 'Simulação', 'Saídas', 'Entradas', 'Cartões', 'Contas', 'Pessoas', 'Categorias']) {
      expect(element.textContent).toContain(section);
    }
  });

  it('links the cards menu to the cards list', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });

    const element = fixture.nativeElement as HTMLElement;
    const cards = Array.from(element.querySelectorAll('a.nav-item')).find(
      (link) => link.textContent?.includes('Cartões'),
    );
    expect(cards?.getAttribute('href')).toBe('/home/cartoes');
  });

  it('links the invoice menu to the invoice consultation page', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });

    const element = fixture.nativeElement as HTMLElement;
    const invoices = Array.from(element.querySelectorAll('a.nav-item')).find(
      (link) => link.textContent?.includes('Fatura'),
    );
    expect(invoices?.getAttribute('href')).toBe('/home/faturas');
  });

  it('links the buckets menu to monthly budget management', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });

    const element = fixture.nativeElement as HTMLElement;
    const buckets = Array.from(element.querySelectorAll('a.nav-item')).find(
      (link) => link.textContent?.includes('Buckets'),
    );
    expect(buckets?.getAttribute('href')).toBe('/home/buckets');
  });

  it('links the simulation menu to the budget simulation page', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });

    const element = fixture.nativeElement as HTMLElement;
    const simulation = Array.from(element.querySelectorAll('a.nav-item')).find(
      (link) => link.textContent?.includes('Simulação'),
    );
    expect(simulation?.getAttribute('href')).toBe('/home/simulacao');
  });

  it('allows the sidebar to be collapsed and expanded', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    http.expectOne('/api/usuarios/me').flush({
      data: { nome: 'Ana', sobrenome: 'Silva' },
    });

    const element = fixture.nativeElement as HTMLElement;
    const toggle = element.querySelector<HTMLButtonElement>('.menu-toggle')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });
});
