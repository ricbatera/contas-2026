import { AbstractControl, ValidationErrors } from '@angular/forms';

const formatoMoeda =new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Formata o texto digitado num campo de valor: só dígitos e uma vírgula, milhar com ponto e
 * no máximo 2 casas. Ex.: "1250,52" vira "1.250,52". Pontos no texto são tratados como milhar.
 * Com `permitirNegativo`, um "-" em qualquer posição torna o valor negativo ("-1.250,52").
 */
export function formatarDigitacaoMoeda(texto: string, permitirNegativo = false): string {
  const negativo = permitirNegativo && texto.includes('-');
  const limpo = texto.replace(/[^\d,]/g, '');
  const virgula = limpo.indexOf(',');
  const inteiroBruto = virgula < 0 ? limpo : limpo.slice(0, virgula);
  const decimais = virgula < 0 ? null : limpo.slice(virgula + 1).replace(/,/g, '').slice(0, 2);

  let inteiro = inteiroBruto.replace(/^0+(?=\d)/, '');
  if (inteiro === '' && decimais !== null) {
    inteiro = '0';
  }
  const agrupado = inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return (negativo ? '-' : '') + (decimais === null ? agrupado : `${agrupado},${decimais}`);
}

/** Converte o texto do campo ("1.250,5" ou "-1.250,5") em número; vazio ou só o sinal vira null. */
export function paraNumeroMoeda(texto: string): number | null {
  const normalizado = texto.replace(/\./g, '').replace(',', '.').trim();
  if (normalizado === '' || normalizado === '.' || normalizado === '-' || normalizado === '-.') {
    return null;
  }
  const valor = Number(normalizado);
  return Number.isFinite(valor) ? valor : null;
}

/** Validator dos campos que aceitam negativo (reembolso): qualquer valor, menos zero. */
export function valorDiferenteDeZero(control: AbstractControl): ValidationErrors | null {
  const valor = control.value;
  return valor !== null && valor !== '' && Number(valor) === 0 ? { zero: true } : null;
}

/** Formata um número com 2 casas no padrão pt-BR, sem símbolo: 1250.5 vira "1.250,50". */
export function formatarValorMoeda(valor: number): string {
  return formatoMoeda.format(valor).replace('−', '-');
}
