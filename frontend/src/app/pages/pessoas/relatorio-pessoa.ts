import { Injectable } from '@angular/core';
import { entregarArquivo } from '../../shared/arquivo/entregar-arquivo';
import { gerarPdfDeImagens } from '../../shared/arquivo/pdf-imagens';

/** Uma parcela da pessoa no relatório, com os textos já formatados para exibição. */
export interface ItemRelatorio {
  descricao: string;
  /** Categoria · subcategoria. */
  detalhe: string;
  /** Vencimento, parcela, forma de pagamento, cartão. */
  info: string;
  valor: number;
  /** Valor da parcela inteira, só quando a pessoa tem apenas uma parte dela. */
  valorParcela: number | null;
}

export interface DadosRelatorioPessoa {
  pessoa: string;
  /** Ex.: "novembro de 2026". */
  competencia: string;
  busca: string;
  total: number;
  itens: ItemRelatorio[];
  progressao: { rotulo: string; total: number }[];
  geradoEm: Date;
}

interface Bloco {
  altura: number;
  desenhar(ctx: CanvasRenderingContext2D, y: number): void;
}

/** Todo o layout é feito numa largura lógica de 1080; o PDF só muda a escala. */
const LARGURA = 1080;
const MARGEM = 56;
const ALTURA_RODAPE = 64;
/** A4 a 150 dpi. */
const PAGINA_PDF = { largura: 1240, altura: 1754 };
const FONTE = 'system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif';
const COR = {
  texto: '#172b4d',
  suave: '#718096',
  claro: '#8995a7',
  linha: '#edf0f4',
  destaque: '#1761b5',
  fundoDestaque: '#eaf3ff',
  barra: '#3278c7',
  eixo: '#e9eef5',
};

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const moedaCompacta = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  maximumFractionDigits: 1,
});

/**
 * Gera e entrega o relatório. É um serviço para que as specs do componente o substituam: o jsdom
 * não desenha em canvas.
 */
@Injectable({ providedIn: 'root' })
export class RelatorioPessoaService {
  async exportar(tipo: 'png' | 'pdf', dados: DadosRelatorioPessoa, nomeArquivo: string): Promise<void> {
    const blob = tipo === 'png' ? await gerarImagem(dados) : await gerarPdf(dados);
    await entregarArquivo(blob, nomeArquivo, `Saídas de ${dados.pessoa} – ${dados.competencia}`);
  }
}

export async function gerarImagem(dados: DadosRelatorioPessoa): Promise<Blob> {
  const blocos = [...blocosDoRelatorio(dados), blocoRodapeImagem(dados)];
  const altura = blocos.reduce((total, bloco) => total + bloco.altura, MARGEM);
  const { canvas, ctx } = criarCanvas(LARGURA, altura);
  pintarFundo(ctx, LARGURA, altura);
  let y = MARGEM;
  for (const bloco of blocos) {
    bloco.desenhar(ctx, y);
    y += bloco.altura;
  }
  return paraBlob(canvas, 'image/png');
}

export async function gerarPdf(dados: DadosRelatorioPessoa): Promise<Blob> {
  const escala = PAGINA_PDF.largura / LARGURA;
  const alturaPagina = PAGINA_PDF.altura / escala;
  const blocos = blocosDoRelatorio(dados);
  const continuacao = blocoContinuacao(dados);
  // A partir da 2ª página, um cabeçalho curto identifica a pessoa e o mês.
  const paginas = paginar(
    blocos.map((bloco) => bloco.altura),
    alturaPagina - MARGEM - ALTURA_RODAPE,
    continuacao.altura,
  );

  const jpegs = [];
  for (const [indice, pagina] of paginas.entries()) {
    const { canvas, ctx } = criarCanvas(PAGINA_PDF.largura, PAGINA_PDF.altura);
    pintarFundo(ctx, PAGINA_PDF.largura, PAGINA_PDF.altura);
    ctx.scale(escala, escala);
    let y = MARGEM;
    if (indice > 0) {
      continuacao.desenhar(ctx, y);
      y += continuacao.altura;
    }
    for (const indiceBloco of pagina) {
      blocos[indiceBloco].desenhar(ctx, y);
      y += blocos[indiceBloco].altura;
    }
    desenharRodapePagina(ctx, alturaPagina, dados, indice + 1, paginas.length);
    const jpeg = new Uint8Array(await (await paraBlob(canvas, 'image/jpeg', 0.9)).arrayBuffer());
    jpegs.push({ jpeg, largura: PAGINA_PDF.largura, altura: PAGINA_PDF.altura });
  }
  return gerarPdfDeImagens(jpegs);
}

/**
 * Distribui os blocos em páginas sem cortar nenhum ao meio. A primeira página tem `alturaUtil`;
 * as seguintes perdem `alturaContinuacao` para o cabeçalho repetido. Um bloco maior que a página
 * fica sozinho nela. Devolve os índices dos blocos de cada página.
 */
export function paginar(alturas: number[], alturaUtil: number, alturaContinuacao = 0): number[][] {
  const paginas: number[][] = [[]];
  let livre = alturaUtil;
  alturas.forEach((altura, indice) => {
    const atual = paginas[paginas.length - 1];
    if (altura > livre && atual.length > 0) {
      paginas.push([indice]);
      livre = alturaUtil - alturaContinuacao - altura;
      return;
    }
    atual.push(indice);
    livre -= altura;
  });
  return paginas;
}

/** Nome de arquivo sem acentos nem espaços: "saidas-ana-maria-2026-11.pdf". */
export function nomeArquivoRelatorio(pessoa: string, mes: number, ano: number, extensao: 'png' | 'pdf'): string {
  const slug = pessoa
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase('pt-BR')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
  return `saidas-${slug || 'pessoa'}-${ano}-${String(mes).padStart(2, '0')}.${extensao}`;
}

function blocosDoRelatorio(dados: DadosRelatorioPessoa): Bloco[] {
  const blocos: Bloco[] = [blocoCabecalho(dados), blocoTotal(dados), blocoTituloLista()];
  if (dados.itens.length === 0) {
    blocos.push(blocoTexto(dados.busca ? `Nenhuma saída encontrada para “${dados.busca}”.` : 'Nenhuma saída neste mês.'));
  } else {
    blocos.push(...dados.itens.map(blocoItem));
  }
  blocos.push(blocoGrafico(dados));
  return blocos;
}

function blocoCabecalho(dados: DadosRelatorioPessoa): Bloco {
  return {
    altura: dados.busca ? 170 : 140,
    desenhar(ctx, y) {
      texto(ctx, 'SAÍDAS POR PESSOA', MARGEM, y + 18, { tamanho: 18, peso: 700, cor: COR.claro, espacamento: 2 });
      texto(ctx, dados.pessoa, MARGEM, y + 68, { tamanho: 44, peso: 700, cor: COR.texto, larguraMaxima: LARGURA - 2 * MARGEM });
      texto(ctx, capitalizar(dados.competencia), MARGEM, y + 108, { tamanho: 26, cor: COR.suave });
      if (dados.busca) {
        texto(ctx, `Filtrado pela busca “${dados.busca}”`, MARGEM, y + 144, {
          tamanho: 20, cor: COR.suave, larguraMaxima: LARGURA - 2 * MARGEM,
        });
      }
    },
  };
}

function blocoContinuacao(dados: DadosRelatorioPessoa): Bloco {
  return {
    altura: 70,
    desenhar(ctx, y) {
      texto(ctx, `${dados.pessoa} · ${capitalizar(dados.competencia)} (continuação)`, MARGEM, y + 24, {
        tamanho: 22, peso: 600, cor: COR.suave, larguraMaxima: LARGURA - 2 * MARGEM,
      });
      linha(ctx, y + 48);
    },
  };
}

function blocoTotal(dados: DadosRelatorioPessoa): Bloco {
  return {
    altura: 170,
    desenhar(ctx, y) {
      retanguloArredondado(ctx, MARGEM, y, LARGURA - 2 * MARGEM, 140, 18, COR.fundoDestaque);
      texto(ctx, dados.busca ? 'TOTAL NO MÊS (BUSCA)' : 'TOTAL NO MÊS', MARGEM + 32, y + 50, {
        tamanho: 20, peso: 700, cor: '#52637a', espacamento: 1,
      });
      texto(ctx, moeda.format(dados.total), MARGEM + 32, y + 110, { tamanho: 52, peso: 700, cor: COR.destaque });
      const quantidade = `${dados.itens.length} ${dados.itens.length === 1 ? 'parcela' : 'parcelas'}`;
      texto(ctx, quantidade, LARGURA - MARGEM - 32, y + 110, { tamanho: 22, cor: '#52637a', alinhamento: 'right' });
    },
  };
}

function blocoTituloLista(): Bloco {
  return {
    altura: 56,
    desenhar(ctx, y) {
      texto(ctx, 'Parcelas do mês', MARGEM, y + 30, { tamanho: 26, peso: 700, cor: COR.texto });
      linha(ctx, y + 52);
    },
  };
}

function blocoItem(item: ItemRelatorio): Bloco {
  return {
    altura: 116,
    desenhar(ctx, y) {
      const larguraTexto = LARGURA - 2 * MARGEM - 260;
      texto(ctx, item.descricao, MARGEM, y + 38, { tamanho: 25, peso: 650, cor: COR.texto, larguraMaxima: larguraTexto });
      texto(ctx, item.detalhe, MARGEM, y + 70, { tamanho: 20, cor: COR.claro, larguraMaxima: larguraTexto });
      texto(ctx, item.info, MARGEM, y + 98, { tamanho: 19, cor: COR.suave, larguraMaxima: larguraTexto });
      texto(ctx, moeda.format(item.valor), LARGURA - MARGEM, y + 44, {
        tamanho: 27, peso: 700, cor: COR.texto, alinhamento: 'right',
      });
      if (item.valorParcela !== null) {
        texto(ctx, `de ${moeda.format(item.valorParcela)}`, LARGURA - MARGEM, y + 76, {
          tamanho: 19, cor: COR.claro, alinhamento: 'right',
        });
      }
      linha(ctx, y + 114);
    },
  };
}

function blocoTexto(conteudo: string): Bloco {
  return {
    altura: 90,
    desenhar(ctx, y) {
      texto(ctx, conteudo, LARGURA / 2, y + 54, { tamanho: 22, cor: COR.suave, alinhamento: 'center' });
    },
  };
}

function blocoGrafico(dados: DadosRelatorioPessoa): Bloco {
  const alturaPlotagem = 300;
  return {
    altura: 500,
    desenhar(ctx, y) {
      texto(ctx, 'Progressão mensal', MARGEM, y + 64, { tamanho: 26, peso: 700, cor: COR.texto });
      texto(ctx, `Total de ${dados.pessoa} por mês, a partir de ${dados.competencia}`, MARGEM, y + 98, {
        tamanho: 19, cor: COR.claro, larguraMaxima: LARGURA - 2 * MARGEM,
      });

      const base = y + 140 + alturaPlotagem;
      const esquerda = MARGEM + 110;
      const direita = LARGURA - MARGEM;
      const maximo = escalaMaxima(Math.max(0, ...dados.progressao.map((mes) => mes.total)));
      for (let indice = 0; indice <= 4; indice++) {
        const valor = maximo * indice / 4;
        const yEixo = base - alturaPlotagem * indice / 4;
        ctx.strokeStyle = COR.eixo;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(esquerda, yEixo);
        ctx.lineTo(direita, yEixo);
        ctx.stroke();
        texto(ctx, rotuloEixo(valor), esquerda - 12, yEixo + 6, { tamanho: 16, cor: COR.claro, alinhamento: 'right' });
      }

      const passo = (direita - esquerda) / Math.max(dados.progressao.length, 1);
      const larguraBarra = Math.min(48, passo * 0.6);
      dados.progressao.forEach((mes, indice) => {
        const centro = esquerda + passo * (indice + 0.5);
        const altura = alturaPlotagem * mes.total / maximo;
        if (altura > 0) {
          retanguloArredondado(ctx, centro - larguraBarra / 2, base - altura, larguraBarra, altura, 3, COR.barra);
          texto(ctx, moedaCompacta.format(mes.total), centro, base - altura - 10, {
            tamanho: dados.progressao.length > 14 ? 12 : 15, peso: 650, cor: '#52637a', alinhamento: 'center',
          });
        }
        texto(ctx, mes.rotulo, centro, base + 30, {
          tamanho: dados.progressao.length > 14 ? 12 : 16, cor: COR.claro, alinhamento: 'center',
        });
      });
    },
  };
}

function blocoRodapeImagem(dados: DadosRelatorioPessoa): Bloco {
  return {
    altura: ALTURA_RODAPE,
    desenhar(ctx, y) {
      texto(ctx, `Gerado em ${formatarDataHora(dados.geradoEm)}`, MARGEM, y + 30, { tamanho: 17, cor: COR.claro });
    },
  };
}

function desenharRodapePagina(
  ctx: CanvasRenderingContext2D,
  alturaPagina: number,
  dados: DadosRelatorioPessoa,
  pagina: number,
  totalPaginas: number,
): void {
  const y = alturaPagina - ALTURA_RODAPE + 30;
  linha(ctx, alturaPagina - ALTURA_RODAPE);
  texto(ctx, `Gerado em ${formatarDataHora(dados.geradoEm)}`, MARGEM, y, { tamanho: 17, cor: COR.claro });
  texto(ctx, `Página ${pagina} de ${totalPaginas}`, LARGURA - MARGEM, y, { tamanho: 17, cor: COR.claro, alinhamento: 'right' });
}

// --- primitivas de desenho ---

interface EstiloTexto {
  tamanho: number;
  peso?: number;
  cor: string;
  alinhamento?: CanvasTextAlign;
  larguraMaxima?: number;
  espacamento?: number;
}

function texto(ctx: CanvasRenderingContext2D, conteudo: string, x: number, y: number, estilo: EstiloTexto): void {
  ctx.font = `${estilo.peso ?? 400} ${estilo.tamanho}px ${FONTE}`;
  ctx.fillStyle = estilo.cor;
  ctx.textAlign = estilo.alinhamento ?? 'left';
  ctx.textBaseline = 'alphabetic';
  if ('letterSpacing' in ctx) {
    (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = `${estilo.espacamento ?? 0}px`;
  }
  const final = estilo.larguraMaxima ? ajustar(ctx, conteudo, estilo.larguraMaxima) : conteudo;
  ctx.fillText(final, x, y);
}

/** Corta o texto com reticências para caber na largura. */
function ajustar(ctx: CanvasRenderingContext2D, conteudo: string, largura: number): string {
  if (ctx.measureText(conteudo).width <= largura) {
    return conteudo;
  }
  let fim = conteudo.length;
  while (fim > 0 && ctx.measureText(`${conteudo.slice(0, fim)}…`).width > largura) {
    fim--;
  }
  return `${conteudo.slice(0, fim).trimEnd()}…`;
}

function linha(ctx: CanvasRenderingContext2D, y: number): void {
  ctx.strokeStyle = COR.linha;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(MARGEM, y);
  ctx.lineTo(LARGURA - MARGEM, y);
  ctx.stroke();
}

function retanguloArredondado(
  ctx: CanvasRenderingContext2D, x: number, y: number, largura: number, altura: number, raio: number, cor: string,
): void {
  const r = Math.min(raio, altura / 2, largura / 2);
  ctx.fillStyle = cor;
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + largura, y, x + largura, y + altura, r);
  ctx.arcTo(x + largura, y + altura, x, y + altura, r);
  ctx.arcTo(x, y + altura, x, y, r);
  ctx.arcTo(x, y, x + largura, y, r);
  ctx.closePath();
  ctx.fill();
}

function criarCanvas(largura: number, altura: number) {
  const canvas = document.createElement('canvas');
  canvas.width = Math.ceil(largura);
  canvas.height = Math.ceil(altura);
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('O navegador não conseguiu desenhar o relatório.');
  }
  return { canvas, ctx };
}

function pintarFundo(ctx: CanvasRenderingContext2D, largura: number, altura: number): void {
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, largura, altura);
}

function paraBlob(canvas: HTMLCanvasElement, tipo: string, qualidade?: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('Não foi possível gerar o arquivo.'))),
      tipo,
      qualidade,
    );
  });
}

function escalaMaxima(maximo: number): number {
  if (maximo === 0) {
    return 1;
  }
  const magnitude = 10 ** Math.floor(Math.log10(maximo));
  const fator = maximo / magnitude;
  return magnitude * (fator <= 1 ? 1 : fator <= 2 ? 2 : fator <= 5 ? 5 : 10);
}

function rotuloEixo(valor: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: Math.abs(valor) >= 100 ? 0 : 2,
  }).format(valor);
}

function formatarDataHora(data: Date): string {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(data);
}

function capitalizar(textoOriginal: string): string {
  return textoOriginal.charAt(0).toLocaleUpperCase('pt-BR') + textoOriginal.slice(1);
}
