import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cartao, CartaoService } from '../../core/cartao.service';
import { FormaPagamento, Saida, SaidaParcela, SaidaService } from '../../core/saida.service';

interface FiltrosSaida {
  texto: string;
  formaPagamento: string;
  categoriaId: string;
  subcategoriaId: string;
  cartaoId: string;
  responsavelId: string;
  contaFixa: string;
  mesVencimento: string;
  anoVencimento: string;
  valorMinimo: string;
  valorMaximo: string;
}

interface OpcaoFiltro {
  id: string;
  nome: string;
}

interface GrupoCartao {
  principal: OpcaoFiltro;
  adicionais: OpcaoFiltro[];
}

const PREFIXO_GRUPO_CARTAO = 'grupo:';

interface SaidaCard {
  key: string;
  saida: Saida;
  parcela: SaidaParcela | null;
  valorParcela: number;
  valorTotalCompra: number;
}

const filtrosVazios = (): FiltrosSaida => ({
  texto: '',
  formaPagamento: '',
  categoriaId: '',
  subcategoriaId: '',
  cartaoId: '',
  responsavelId: '',
  contaFixa: '',
  mesVencimento: '',
  anoVencimento: '',
  valorMinimo: '',
  valorMaximo: '',
});

const filtrosMesAtual = (): FiltrosSaida => {
  const hoje = new Date();
  return {
    ...filtrosVazios(),
    mesVencimento: String(hoje.getMonth() + 1).padStart(2, '0'),
    anoVencimento: String(hoje.getFullYear()),
  };
};

const MESES = Array.from({ length: 12 }, (_, index) => ({
  id: String(index + 1).padStart(2, '0'),
  nome: new Intl.DateTimeFormat('pt-BR', { month: 'long', timeZone: 'UTC' })
    .format(new Date(Date.UTC(2020, index, 1))),
}));

@Component({
  selector: 'app-saidas-list',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './saidas-list.html',
  styleUrls: ['../cartoes/cartoes.css', './saidas.css'],
})
export class SaidasList implements OnInit {
  private readonly service = inject(SaidaService);
  private readonly cartaoService = inject(CartaoService);

  protected readonly saidas = signal<Saida[]>([]);
  private readonly cartoes = signal<Cartao[]>([]);
  private readonly cartoesPorId = computed(() => new Map(this.cartoes().map((cartao) => [cartao.id, cartao])));
  protected readonly saidasFiltradas = computed(() => {
    const filtros = this.filtrosAplicados();
    const texto = filtros.texto.trim().toLocaleLowerCase('pt-BR');
    const valorMinimo = filtros.valorMinimo === '' ? null : Number(filtros.valorMinimo);
    const valorMaximo = filtros.valorMaximo === '' ? null : Number(filtros.valorMaximo);

    return this.saidas().filter((saida) => {
      if (texto && !`${saida.descricao} ${saida.descricaoFatura ?? ''}`.toLocaleLowerCase('pt-BR').includes(texto)) {
        return false;
      }
      if (filtros.formaPagamento && saida.formaPagamento !== filtros.formaPagamento) {
        return false;
      }
      if (filtros.categoriaId && saida.categoriaId !== filtros.categoriaId) {
        return false;
      }
      if (filtros.subcategoriaId && saida.subcategoriaId !== filtros.subcategoriaId) {
        return false;
      }
      if (filtros.cartaoId && !this.cartaoNoFiltro(saida.cartaoId, filtros.cartaoId)) {
        return false;
      }
      const responsabilidadeSelecionada = filtros.responsavelId
        ? this.responsabilidade(saida, filtros.responsavelId)
        : null;
      if (filtros.responsavelId && !responsabilidadeSelecionada) {
        return false;
      }
      if (filtros.contaFixa === 'SIM' && !saida.contaFixa) {
        return false;
      }
      if (filtros.contaFixa === 'NAO' && saida.contaFixa) {
        return false;
      }
      const valorPorParcela = responsabilidadeSelecionada?.valorPorParcela ?? saida.valorPorParcela;
      if (valorMinimo !== null && valorPorParcela < valorMinimo) {
        return false;
      }
      if (valorMaximo !== null && valorPorParcela > valorMaximo) {
        return false;
      }
      if (filtros.mesVencimento && filtros.anoVencimento) {
        const temParcelaNoPeriodo = saida.parcelas.some((parcela) => this.parcelaNaCompetencia(parcela, filtros));
        if (!temParcelaNoPeriodo) {
          return false;
        }
      }
      return true;
    });
  });
  protected readonly cardsVisiveis = computed<SaidaCard[]>(() => {
    const filtros = this.filtrosAplicados();
    return this.saidasFiltradas().flatMap((saida) => {
      const responsabilidadeSelecionada = filtros.responsavelId
        ? this.responsabilidade(saida, filtros.responsavelId)
        : null;
      if (filtros.responsavelId && !responsabilidadeSelecionada) {
        return [];
      }
      const parcelas = [...saida.parcelas].sort((a, b) =>
        a.vencimento.localeCompare(b.vencimento) || a.numero - b.numero,
      );
      const parcelasExibidas = filtros.mesVencimento && filtros.anoVencimento
        ? parcelas.filter((parcela) => this.parcelaNaCompetencia(parcela, filtros))
        : [this.parcelaDestaque(saida)];

      return parcelasExibidas.map((parcela) => ({
        key: `${saida.id}:${parcela?.id ?? 'sem-parcela'}`,
        saida,
        parcela,
        valorParcela: responsabilidadeSelecionada?.valorPorParcela ?? parcela?.valor ?? 0,
        valorTotalCompra: responsabilidadeSelecionada
          ? Math.round(responsabilidadeSelecionada.valorPorParcela * 100) * saida.quantidadeParcelas / 100
          : saida.valorTotal,
      }));
    });
  });
  protected readonly totalCardsVisiveis = computed(() =>
    this.cardsVisiveis().reduce(
      (totalCentavos, card) => totalCentavos + Math.round(card.valorParcela * 100),
      0,
    ) / 100,
  );
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly workingId = signal<string | null>(null);
  protected readonly filtrosAbertos = signal(false);
  protected readonly filtrosRascunho = signal<FiltrosSaida>(filtrosMesAtual());
  protected readonly filtrosAplicados = signal<FiltrosSaida>(filtrosMesAtual());
  protected readonly erroFiltros = signal<string | null>(null);
  protected readonly quantidadeFiltrosAtivos = computed(() =>
    Object.values(this.filtrosAplicados()).filter(Boolean).length,
  );
  protected readonly categoriasDisponiveis = computed(() =>
    this.opcoesUnicas(this.saidas().map((saida) => ({ id: saida.categoriaId, nome: saida.categoriaNome }))),
  );
  protected readonly subcategoriasDisponiveis = computed(() => {
    const categoriaId = this.filtrosRascunho().categoriaId;
    return this.opcoesUnicas(
      this.saidas()
        .filter((saida) => !categoriaId || saida.categoriaId === categoriaId)
        .map((saida) => ({ id: saida.subcategoriaId, nome: saida.subcategoriaNome })),
    );
  });
  // Adicionais ficam agrupados sob o principal, que também pode ser filtrado junto com eles.
  protected readonly gruposCartao = computed<GrupoCartao[]>(() => {
    const usados = this.opcoesUnicas(
      this.saidas()
        .filter((saida) => saida.cartaoId && saida.cartaoNome)
        .map((saida) => ({ id: saida.cartaoId!, nome: saida.cartaoNome! })),
    );
    const cartoes = this.cartoesPorId();
    const grupos = new Map<string, GrupoCartao>();
    for (const opcao of usados) {
      const principalId = cartoes.get(opcao.id)?.cartaoPrincipalId;
      const principal = principalId ? cartoes.get(principalId) : undefined;
      if (principal) {
        const grupo = grupos.get(principal.id) ?? { principal: { id: principal.id, nome: principal.nome }, adicionais: [] };
        grupo.adicionais.push(opcao);
        grupos.set(principal.id, grupo);
      } else {
        grupos.set(opcao.id, { principal: opcao, adicionais: grupos.get(opcao.id)?.adicionais ?? [] });
      }
    }
    return [...grupos.values()].sort((a, b) => a.principal.nome.localeCompare(b.principal.nome, 'pt-BR'));
  });
  protected readonly responsaveisDisponiveis = computed(() =>
    this.opcoesUnicas(
      this.saidas().flatMap((saida) =>
        saida.responsabilidades
          .filter((responsavel) => responsavel.pessoaId || responsavel.usuarioId)
          .map((responsavel) => ({
            id: this.idResponsavel(responsavel.pessoaId, responsavel.usuarioId),
            nome: responsavel.nome,
          })),
      ),
    ),
  );
  protected readonly mesesDisponiveis = MESES;
  protected readonly anosDisponiveis = computed(() => {
    const anos = new Set<string>([String(new Date().getFullYear())]);
    for (const saida of this.saidas()) {
      for (const parcela of saida.parcelas) {
        const ano = parcela.vencimento.slice(0, 4);
        if (/^\d{4}$/.test(ano)) {
          anos.add(ano);
        }
      }
    }
    return [...anos].sort((a, b) => Number(a) - Number(b));
  });

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listar().subscribe({
      next: (saidas) => {
        this.saidas.set(saidas);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar as saídas.'));
        this.loading.set(false);
      },
    });
    // Sem a lista de cartões, o filtro só perde o agrupamento por cartão principal.
    this.cartaoService.listar().subscribe({
      next: (cartoes) => this.cartoes.set(cartoes),
      error: () => this.cartoes.set([]),
    });
  }

  protected valorGrupoCartao(principalId: string): string {
    return `${PREFIXO_GRUPO_CARTAO}${principalId}`;
  }

  protected nomeForma(forma: FormaPagamento): string {
    return {
      CARTAO_CREDITO: 'Cartão de crédito',
      BOLETO: 'Boleto',
      DEBITO: 'Débito',
      PIX: 'Pix',
    }[forma];
  }

  protected parcelaDestaque(saida: Saida): SaidaParcela | null {
    const parcelas = [...saida.parcelas].sort((a, b) =>
      a.vencimento.localeCompare(b.vencimento) || a.numero - b.numero,
    );
    const filtros = this.filtrosAplicados();
    if (filtros.mesVencimento && filtros.anoVencimento) {
      return parcelas.find((parcela) => this.parcelaNaCompetencia(parcela, filtros)) ?? null;
    }

    const hoje = new Date();
    const hojeFormatado = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
    return parcelas.find((parcela) => parcela.vencimento >= hojeFormatado)
      ?? parcelas.at(-1)
      ?? null;
  }

  protected abrirFiltros(): void {
    this.filtrosRascunho.set({ ...this.filtrosAplicados() });
    this.erroFiltros.set(null);
    this.filtrosAbertos.set(true);
  }

  protected atualizarFiltro(campo: keyof FiltrosSaida, event: Event): void {
    const valor = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.filtrosRascunho.update((filtros) => ({
      ...filtros,
      [campo]: valor,
      ...(campo === 'categoriaId' ? { subcategoriaId: '' } : {}),
    }));
    this.erroFiltros.set(null);
  }

  protected aplicarFiltros(): void {
    const filtros = this.filtrosRascunho();
    const minimo = filtros.valorMinimo === '' ? null : Number(filtros.valorMinimo);
    const maximo = filtros.valorMaximo === '' ? null : Number(filtros.valorMaximo);
    if (minimo !== null && (!Number.isFinite(minimo) || minimo < 0)
      || maximo !== null && (!Number.isFinite(maximo) || maximo < 0)) {
      this.erroFiltros.set('Informe valores mínimos e máximos válidos e não negativos.');
      return;
    }
    if (minimo !== null && maximo !== null && minimo > maximo) {
      this.erroFiltros.set('O valor mínimo não pode ser maior que o valor máximo.');
      return;
    }
    this.filtrosAplicados.set({ ...filtros });
    this.erroFiltros.set(null);
    this.filtrosAbertos.set(false);
  }

  protected limparFiltros(): void {
    const filtros = filtrosMesAtual();
    this.filtrosRascunho.set(filtros);
    this.filtrosAplicados.set(filtros);
    this.erroFiltros.set(null);
  }

  protected cancelarFiltros(): void {
    this.filtrosRascunho.set({ ...this.filtrosAplicados() });
    this.erroFiltros.set(null);
    this.filtrosAbertos.set(false);
  }

  protected excluir(saida: Saida): void {
    if (!window.confirm(`Deseja excluir "${saida.descricao}" e suas parcelas? Essa ação não pode ser desfeita.`)) {
      return;
    }
    this.workingId.set(saida.id);
    this.error.set(null);
    this.service.excluir(saida.id).subscribe({
      next: () => {
        this.saidas.update((items) => items.filter((item) => item.id !== saida.id));
        this.workingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, `Não foi possível excluir "${saida.descricao}".`));
        this.workingId.set(null);
      },
    });
  }

  private mensagemErro(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
    }
    return typeof err.error?.message === 'string'
      ? err.error.message
      : `${fallback} (erro ${err.status}).`;
  }

  private parcelaNaCompetencia(parcela: SaidaParcela, filtros: FiltrosSaida): boolean {
    return parcela.vencimento.startsWith(`${filtros.anoVencimento}-${filtros.mesVencimento}-`);
  }

  private cartaoNoFiltro(cartaoId: string | null, filtro: string): boolean {
    if (!filtro.startsWith(PREFIXO_GRUPO_CARTAO)) {
      return cartaoId === filtro;
    }
    const principalId = filtro.slice(PREFIXO_GRUPO_CARTAO.length);
    return cartaoId === principalId
      || !!cartaoId && this.cartoesPorId().get(cartaoId)?.cartaoPrincipalId === principalId;
  }

  private opcoesUnicas(opcoes: OpcaoFiltro[]): OpcaoFiltro[] {
    return [...new Map(opcoes.map((opcao) => [opcao.id, opcao])).values()]
      .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
  }

  private idResponsavel(pessoaId: string | null, usuarioId: string | null): string {
    return pessoaId ?? usuarioId ?? '';
  }

  private responsabilidade(saida: Saida, responsavelId: string) {
    return saida.responsabilidades.find(
      (responsavel) => this.idResponsavel(responsavel.pessoaId, responsavel.usuarioId) === responsavelId,
    );
  }
}
