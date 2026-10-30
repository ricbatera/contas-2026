import { formatarValorMoeda } from '../moeda/moeda';

export type CampoBuscavel = string | number | null | undefined;

/** Minúsculas e sem acentos, para "Água" casar com "agua". */
export function normalizarBusca(texto: string): string {
  return texto.trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/\p{Diacritic}/gu, '');
}

/**
 * Se o item casa com o termo da busca: cada palavra do termo precisa aparecer em algum dos campos.
 * Números são comparados no formato pt-BR, com e sem separador de milhar ("1.250,50" e "1250,50"),
 * então "150", "150,00" e "1.250" encontram os valores. Termo vazio casa com tudo.
 */
export function correspondeBusca(termo: string, ...campos: CampoBuscavel[]): boolean {
  const palavras = normalizarBusca(termo).split(/\s+/).filter(Boolean);
  if (palavras.length === 0) {
    return true;
  }
  const texto = campos.map(textoDoCampo).join(' | ');
  return palavras.every((palavra) => texto.includes(palavra));
}

function textoDoCampo(campo: CampoBuscavel): string {
  if (campo === null || campo === undefined) {
    return '';
  }
  if (typeof campo === 'number') {
    const formatado = formatarValorMoeda(campo);
    return `${formatado} ${formatado.replace(/\./g, '')}`;
  }
  return normalizarBusca(campo);
}
