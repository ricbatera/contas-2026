import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Faturas } from './faturas';

describe('Faturas', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Faturas],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the current available statement and displays its installments', () => {
    const fixture = TestBed.createComponent(Faturas);
    fixture.detectChanges();
    http.expectOne('/api/cartoes/principais').flush({
      data: [{
        id: 'card-1',
        nome: 'Principal',
        banco: 'Banco',
        bandeira: 'VISA',
        ultimosDigitos: '1234',
        cor: '#123456',
        criadoEm: '2026-10-09T00:00:00',
        tipo: 'PRINCIPAL',
        virtual: false,
        cartaoPrincipalId: null,
        ativo: true,
      }],
    });

    const currentCompetence = new Date().getFullYear() * 100 + new Date().getMonth() + 1;
    const olderCompetence = currentCompetence - 1;
    http.expectOne('/api/faturas/cartoes/card-1/competencias').flush({
      data: [
        { competencia: currentCompetence + 1, vencimento: '2026-12-15', total: 25 },
        { competencia: currentCompetence, vencimento: '2026-11-15', total: 100 },
        { competencia: olderCompetence, vencimento: '2026-10-15', total: 50 },
      ],
    });
    http.expectOne(`/api/faturas/cartoes/card-1/${currentCompetence}`).flush({
      data: {
        cartaoId: 'card-1',
        cartaoNome: 'Principal',
        banco: 'Banco',
        competencia: currentCompetence,
        vencimento: '2026-11-15',
        total: 100,
        parcelas: [{
          saidaId: 'saida-1',
          descricao: 'Compra no adicional',
          descricaoFatura: 'Mercado',
          cartaoId: 'card-2',
          cartaoNome: 'Adicional',
          numeroParcela: 1,
          quantidadeParcelas: 2,
          vencimento: '2026-11-15',
          valor: 100,
          responsaveis: [{
            pessoaId: null,
            usuarioId: 'user-1',
            nome: 'Ana Silva',
            valorPorParcela: 100,
          }],
        }],
      },
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Principal');
    expect(element.textContent).toContain('Mercado');
    expect(element.textContent).toContain('Compra no adicional');
    expect(element.textContent).toContain('Cartão adicional: Adicional');
    expect(element.textContent).toContain('Responsáveis: Ana Silva');
    expect(element.textContent).toContain('Total da fatura');
  });

  it('shows an empty state when the selected card has no statements', () => {
    const fixture = TestBed.createComponent(Faturas);
    fixture.detectChanges();
    http.expectOne('/api/cartoes/principais').flush({
      data: [{
        id: 'card-1',
        nome: 'Principal',
        banco: 'Banco',
        bandeira: 'VISA',
        ultimosDigitos: '1234',
        cor: '#123456',
        criadoEm: '2026-10-09T00:00:00',
        tipo: 'PRINCIPAL',
        virtual: false,
        cartaoPrincipalId: null,
        ativo: true,
      }],
    });
    http.expectOne('/api/faturas/cartoes/card-1/competencias').flush({ data: [] });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma fatura para este cartão');
  });

  it('loads a previous or future statement when the competence changes', () => {
    const fixture = TestBed.createComponent(Faturas);
    fixture.detectChanges();
    http.expectOne('/api/cartoes/principais').flush({
      data: [{
        id: 'card-1',
        nome: 'Principal',
        banco: 'Banco',
        bandeira: 'VISA',
        ultimosDigitos: '1234',
        cor: '#123456',
        criadoEm: '2026-10-09T00:00:00',
        tipo: 'PRINCIPAL',
        virtual: false,
        cartaoPrincipalId: null,
        ativo: true,
      }],
    });
    const currentCompetence = new Date().getFullYear() * 100 + new Date().getMonth() + 1;
    http.expectOne('/api/faturas/cartoes/card-1/competencias').flush({
      data: [
        { competencia: currentCompetence + 1, vencimento: '2026-12-15', total: 75 },
        { competencia: currentCompetence, vencimento: '2026-11-15', total: 50 },
      ],
    });
    http.expectOne(`/api/faturas/cartoes/card-1/${currentCompetence}`).flush({
      data: {
        cartaoId: 'card-1',
        cartaoNome: 'Principal',
        banco: 'Banco',
        competencia: currentCompetence,
        vencimento: '2026-11-15',
        total: 50,
        parcelas: [],
      },
    });
    fixture.detectChanges();

    const competenceSelect = (fixture.nativeElement as HTMLElement)
      .querySelectorAll('select')[1] as HTMLSelectElement;
    competenceSelect.value = String(currentCompetence + 1);
    competenceSelect.dispatchEvent(new Event('change'));

    http.expectOne(`/api/faturas/cartoes/card-1/${currentCompetence + 1}`).flush({
      data: {
        cartaoId: 'card-1',
        cartaoNome: 'Principal',
        banco: 'Banco',
        competencia: currentCompetence + 1,
        vencimento: '2026-12-15',
        total: 75,
        parcelas: [],
      },
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('75,00');
  });
});
