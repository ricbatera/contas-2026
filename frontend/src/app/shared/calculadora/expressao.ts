/**
 * Avalia uma expressão aritmética simples (+ - * / e parênteses, com vírgula ou ponto decimal)
 * sem usar eval. Devolve o resultado com 2 casas, ou null se a expressão for inválida.
 */
export function avaliarExpressao(texto: string): number | null {
  const tokens = tokenizar(texto.replace(/×/g, '*').replace(/÷/g, '/').replace(/−/g, '-'));
  if (!tokens || tokens.length === 0) {
    return null;
  }
  let pos = 0;

  const expressao = (): number => {
    let valor = termo();
    while (tokens[pos] === '+' || tokens[pos] === '-') {
      const op = tokens[pos++];
      const direita = termo();
      valor = op === '+' ? valor + direita : valor - direita;
    }
    return valor;
  };

  const termo = (): number => {
    let valor = fator();
    while (tokens[pos] === '*' || tokens[pos] === '/') {
      const op = tokens[pos++];
      const direita = fator();
      if (op === '/' && direita === 0) {
        throw new Error('divisão por zero');
      }
      valor = op === '*' ? valor * direita : valor / direita;
    }
    return valor;
  };

  const fator = (): number => {
    const token = tokens[pos++];
    if (token === '-') {
      return -fator();
    }
    if (token === '+') {
      return fator();
    }
    if (token === '(') {
      const valor = expressao();
      if (tokens[pos++] !== ')') {
        throw new Error('parêntese não fechado');
      }
      return valor;
    }
    if (typeof token === 'number') {
      return token;
    }
    throw new Error('expressão incompleta');
  };

  try {
    const valor = expressao();
    if (pos !== tokens.length || !Number.isFinite(valor)) {
      return null;
    }
    return Math.round(valor * 100) / 100 + 0;
  } catch {
    return null;
  }
}

function tokenizar(texto: string): (number | string)[] | null {
  const tokens: (number | string)[] = [];
  const padrao = /\s*(?:(\d+(?:[.,]\d*)?|[.,]\d+)|([-+*/()]))/y;
  const resto = texto.trim();
  padrao.lastIndex = 0;
  while (padrao.lastIndex < resto.length) {
    const encontrado = padrao.exec(resto);
    if (!encontrado) {
      return null;
    }
    tokens.push(encontrado[1] !== undefined ? Number(encontrado[1].replace(',', '.')) : encontrado[2]);
  }
  return tokens;
}
