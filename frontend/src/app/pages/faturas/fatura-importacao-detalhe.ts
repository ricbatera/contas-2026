import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, Observable, of, switchMap, tap } from 'rxjs';
import { Cartao, CartaoService } from '../../core/cartao.service';
import { Categoria, CategoriaService } from '../../core/categoria.service';
import { FaturaImportacao, FaturaImportacaoService } from '../../core/fatura-importacao.service';
import { ImportacaoMonitorService } from '../../core/importacao-monitor.service';
import { PreSaida, PreSaidaRequest, PreSaidaService } from '../../core/pre-saida.service';
import { mensagemErro, ROTULO_STATUS, rotuloCompetencia } from './importacao-util';
import { CampoCalculadora } from '../../shared/calculadora/campo-calculadora';

/** Ajustes do usuário numa pré-saída pendente, ainda não gravados. */
interface Rascunho extends PreSaidaRequest {
  alterado: boolean;
}

@Component({
  selector: 'app-fatura-importacao-detalhe',
  imports: [CampoCalculadora, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './fatura-importacao-detalhe.html',
  styleUrls: ['../cartoes/cartoes.css', './faturas.css', './importacao.css'],
})
export class FaturaImportacaoDetalhe implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly importacaoService = inject(FaturaImportacaoService);
  private readonly preSaidaService = inject(PreSaidaService);
  private readonly cartaoService = inject(CartaoService);
  private readonly categoriaService = inject(CategoriaService);
  private readonly monitor = inject(ImportacaoMonitorService);

  protected readonly importacao = signal<FaturaImportacao | null>(null);
  protected readonly cartoes = signal<Cartao[]>([]);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly rascunhos = signal<Record<string, Rascunho>>({});
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly mensagem = signal<string | null>(null);
  protected readonly errosItem = signal<Record<string, string>>({});
  protected readonly ocupado = signal<string | null>(null);
  protected readonly confirmar = signal<'reprocessar' | 'excluir' | null>(null);
  protected readonly rotuloStatus = ROTULO_STATUS;
  protected readonly rotuloCompetencia = rotuloCompetencia;
  protected readonly parcelas = Array.from({ length: 60 }, (_, index) => index + 1);

  protected readonly pendentes = computed(() =>
    (this.importacao()?.preSaidas ?? []).filter((item) => item.status === 'PENDENTE'));
  protected readonly aprovadas = computed(() =>
    (this.importacao()?.preSaidas ?? []).filter((item) => item.status === 'APROVADA'));
  protected readonly rejeitadas = computed(() =>
    (this.importacao()?.preSaidas ?? []).filter((item) => item.status === 'REJEITADA'));
  protected readonly vinculadas = computed(() =>
    (this.importacao()?.preSaidas ?? []).filter((item) => item.status === 'VINCULADA'));
  /** Completas e sem sugestão em aberto: aprovar uma com sugestão poderia duplicar uma saída. */
  protected readonly prontas = computed(() =>
    this.pendentes().filter((item) => !item.saidaSugeridaId && this.completa(this.rascunhos()[item.id])));
  protected readonly comSugestao = computed(() => this.pendentes().filter((item) => item.saidaSugeridaId).length);
  /** Cartão principal da fatura e seus adicionais. */
  protected readonly cartoesDaFatura = computed(() => {
    const principalId = this.importacao()?.cartaoPrincipalId;
    return this.cartoes().filter((cartao) =>
      !principalId || cartao.id === principalId || cartao.cartaoPrincipalId === principalId);
  });
  protected readonly categoriasDespesa = computed(() =>
    this.categorias().filter((categoria) => categoria.tipo === 'DESPESA' && categoria.ativo));

  protected readonly processando = computed(() => this.importacao()?.status === 'PROCESSANDO');

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  constructor() {
    // Esta tela se atualiza sozinha quando o monitor vê o fim do processamento, sem alerta flutuante.
    this.monitor.visualizando.set(this.id);
    inject(DestroyRef).onDestroy(() => {
      if (this.monitor.visualizando() === this.id) {
        this.monitor.visualizando.set(null);
      }
    });
    effect(() => {
      const status = this.monitor.statusDe(this.id)?.status;
      if (status && status !== 'PROCESSANDO' && untracked(this.processando)) {
        untracked(() => this.recarregarImportacao());
      }
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      importacao: this.importacaoService.buscarPorId(this.id),
      cartoes: this.cartaoService.listar(),
      categorias: this.categoriaService.listar(),
    }).subscribe({
      next: ({ importacao, cartoes, categorias }) => {
        this.cartoes.set(cartoes);
        this.categorias.set(categorias);
        this.aplicar(importacao);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErro(err, 'Não foi possível carregar a importação.'));
        this.loading.set(false);
      },
    });
  }

  protected subcategoriasDe(categoriaId: string | null) {
    return this.categorias()
      .find((categoria) => categoria.id === categoriaId)
      ?.subcategorias.filter((subcategoria) => subcategoria.ativo) ?? [];
  }

  protected alterar(id: string, campo: keyof PreSaidaRequest, event: Event): void {
    const valor = (event.target as HTMLInputElement | HTMLSelectElement).value;
    const atual = this.rascunhos()[id];
    const proximo: Rascunho = { ...atual, alterado: true };
    switch (campo) {
      case 'descricao':
        proximo.descricao = valor;
        break;
      case 'valorPorParcela':
        proximo.valorPorParcela = Number(valor);
        break;
      case 'numeroParcela':
      case 'quantidadeParcelas':
        proximo[campo] = Number(valor);
        break;
      case 'categoriaId':
        proximo.categoriaId = valor || null;
        proximo.subcategoriaId = null;
        break;
      default:
        proximo[campo] = valor || null;
    }
    this.rascunhos.update((rascunhos) => ({ ...rascunhos, [id]: proximo }));
    this.limparErro(id);
  }

  protected completa(rascunho: Rascunho | undefined): boolean {
    return !!rascunho
      && !!rascunho.descricao.trim()
      && !!rascunho.cartaoId
      && !!rascunho.categoriaId
      && !!rascunho.subcategoriaId
      && rascunho.valorPorParcela > 0
      && rascunho.numeroParcela <= rascunho.quantidadeParcelas;
  }

  protected salvar(preSaida: PreSaida): void {
    this.executar(preSaida.id, this.gravar(preSaida.id), 'Pré-saída atualizada.');
  }

  protected aprovar(preSaida: PreSaida): void {
    this.executar(
      preSaida.id,
      this.gravar(preSaida.id).pipe(switchMap(() => this.preSaidaService.aprovar(preSaida.id))),
      'Pré-saída aprovada e incluída nas saídas.',
    );
  }

  protected rejeitar(preSaida: PreSaida): void {
    this.executar(preSaida.id, this.preSaidaService.rejeitar(preSaida.id), 'Pré-saída rejeitada.');
  }

  protected vincular(preSaida: PreSaida): void {
    this.executar(
      preSaida.id,
      this.preSaidaService.vincular(preSaida.id),
      `Vinculada a "${preSaida.saidaSugeridaDescricao}". A descrição da fatura foi gravada na saída.`,
    );
  }

  protected descartarSugestao(preSaida: PreSaida): void {
    this.executar(
      preSaida.id,
      this.preSaidaService.descartarSugestao(preSaida.id),
      'Sugestão descartada. Revise e aprove como compra nova.',
    );
  }

  protected reabrir(preSaida: PreSaida): void {
    this.executar(preSaida.id, this.preSaidaService.reabrir(preSaida.id), 'Pré-saída reaberta.');
  }

  protected aprovarProntas(): void {
    const prontas = this.prontas();
    if (!prontas.length || this.ocupado()) {
      return;
    }
    this.ocupado.set('lote');
    this.mensagem.set(null);
    const gravacoes = prontas.map((item) => this.gravar(item.id));
    forkJoin(gravacoes)
      .pipe(switchMap(() => this.preSaidaService.aprovarLote(prontas.map((item) => item.id))))
      .subscribe({
        next: (resultado) => {
          const erros: Record<string, string> = {};
          resultado.falhas.forEach((falha) => (erros[falha.id] = falha.mensagem));
          this.errosItem.set(erros);
          this.mensagem.set(`${resultado.aprovadas.length} pré-saída(s) aprovada(s)`
            + (resultado.falhas.length ? `, ${resultado.falhas.length} com erro.` : '.'));
          this.recarregarImportacao();
        },
        error: (err: HttpErrorResponse) => {
          this.ocupado.set(null);
          this.error.set(mensagemErro(err, 'Não foi possível aprovar as pré-saídas.'));
        },
      });
  }

  protected verPdf(): void {
    this.importacaoService.baixarPdf(this.id).subscribe({
      next: (pdf) => {
        const url = URL.createObjectURL(pdf);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err: HttpErrorResponse) => this.error.set(mensagemErro(err, 'Não foi possível abrir o PDF.')),
    });
  }

  protected reprocessar(): void {
    this.confirmar.set(null);
    this.ocupado.set('reprocessar');
    this.error.set(null);
    this.mensagem.set(null);
    this.importacaoService.reprocessar(this.id).subscribe({
      next: (importacao) => {
        this.aplicar(importacao);
        this.ocupado.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(null);
        this.error.set(mensagemErro(err, 'Não foi possível reprocessar a fatura.'));
      },
    });
  }

  protected excluir(): void {
    this.confirmar.set(null);
    this.ocupado.set('excluir');
    this.importacaoService.excluir(this.id).subscribe({
      next: () => this.router.navigate(['/home/faturas/importacoes']),
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(null);
        this.error.set(mensagemErro(err, 'Não foi possível excluir a importação.'));
      },
    });
  }

  protected rotuloCartao(cartao: Cartao): string {
    return `${cartao.nome} · final ${cartao.ultimosDigitos}${cartao.tipo === 'ADICIONAL' ? ' (adicional)' : ''}`;
  }

  private gravar(id: string): Observable<unknown> {
    const rascunho = this.rascunhos()[id];
    if (!rascunho?.alterado) {
      return of(null);
    }
    const { alterado, ...request } = rascunho;
    return this.preSaidaService.atualizar(id, { ...request, descricao: request.descricao.trim() }).pipe(
      tap(() => this.rascunhos.update((rascunhos) => ({ ...rascunhos, [id]: { ...rascunho, alterado: false } }))),
    );
  }

  private executar(id: string, acao: Observable<unknown>, sucesso: string): void {
    if (this.ocupado()) {
      return;
    }
    this.ocupado.set(id);
    this.mensagem.set(null);
    this.limparErro(id);
    acao.subscribe({
      next: () => {
        this.mensagem.set(sucesso);
        this.recarregarImportacao();
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(null);
        this.errosItem.update((erros) => ({ ...erros, [id]: mensagemErro(err, 'Não foi possível concluir a ação.') }));
      },
    });
  }

  private recarregarImportacao(): void {
    this.importacaoService.buscarPorId(this.id).subscribe({
      next: (importacao) => {
        this.aplicar(importacao);
        this.ocupado.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.ocupado.set(null);
        this.error.set(mensagemErro(err, 'Não foi possível atualizar a importação.'));
      },
    });
  }

  /** Mantém os ajustes ainda não gravados das pré-saídas que continuam pendentes. */
  private aplicar(importacao: FaturaImportacao): void {
    const anteriores = this.rascunhos();
    const rascunhos: Record<string, Rascunho> = {};
    importacao.preSaidas
      .filter((item) => item.status === 'PENDENTE')
      .forEach((item) => {
        rascunhos[item.id] = anteriores[item.id]?.alterado ? anteriores[item.id] : {
          descricao: item.descricao,
          cartaoId: item.cartaoId,
          categoriaId: item.categoriaId,
          subcategoriaId: item.subcategoriaId,
          valorPorParcela: item.valorPorParcela,
          numeroParcela: item.numeroParcela,
          quantidadeParcelas: item.quantidadeParcelas,
          alterado: false,
        };
      });
    this.rascunhos.set(rascunhos);
    const terminouAgora = this.processando() && importacao.status !== 'PROCESSANDO';
    this.importacao.set(importacao);
    if (importacao.status === 'PROCESSANDO') {
      this.monitor.acompanhar({
        id: importacao.id,
        nomeArquivo: importacao.nomeArquivo,
        status: importacao.status,
        erro: null,
        preSaidasPendentes: 0,
        atualizadoEm: null,
      });
    } else if (terminouAgora) {
      this.mensagem.set(importacao.status === 'CONCLUIDA'
        ? 'Leitura concluída. Revise as pré-saídas abaixo.'
        : null);
    }
  }

  private limparErro(id: string): void {
    if (this.errosItem()[id]) {
      this.errosItem.update(({ [id]: _, ...resto }) => resto);
    }
  }
}
