import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { PessoasList } from './pessoas-list';

describe('PessoasList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PessoasList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const pessoas = [
    { id: 'pessoa-1', nome: 'Maria Silva', ativo: true, criadoEm: null },
    { id: 'pessoa-2', nome: 'João Souza', ativo: false, criadoEm: null },
  ];

  it('renders active and inactive people', () => {
    const fixture = TestBed.createComponent(PessoasList);
    fixture.detectChanges();
    http.expectOne('/api/pessoas').flush({ data: pessoas });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Maria Silva');
    expect(element.textContent).toContain('João Souza');
    expect(element.textContent).toContain('Ativa');
    expect(element.textContent).toContain('Inativa');
    expect(element.querySelector('[aria-label="Editar pessoa Maria Silva"]')).toBeTruthy();
  });

  it('shows an empty state when there are no people', () => {
    const fixture = TestBed.createComponent(PessoasList);
    fixture.detectChanges();
    http.expectOne('/api/pessoas').flush({ data: [] });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma pessoa cadastrada');
  });

  it('asks for confirmation and removes a person after successful delete', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(PessoasList);
    fixture.detectChanges();
    http.expectOne('/api/pessoas').flush({ data: pessoas });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('[aria-label="Excluir pessoa Maria Silva"]')!.click();
    expect(confirm).toHaveBeenCalledOnce();
    const deletion = http.expectOne('/api/pessoas/pessoa-1');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    fixture.detectChanges();

    expect(element.textContent).not.toContain('Maria Silva');
    expect(element.textContent).toContain('João Souza');
    confirm.mockRestore();
  });

  it('does not delete a person when confirmation is declined', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const fixture = TestBed.createComponent(PessoasList);
    fixture.detectChanges();
    http.expectOne('/api/pessoas').flush({ data: pessoas });
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[aria-label="Excluir pessoa Maria Silva"]')!
      .click();
    expect(http.match('/api/pessoas/pessoa-1')).toHaveLength(0);
    confirm.mockRestore();
  });
});
