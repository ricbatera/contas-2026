/** Até este dia, as telas abrem no mês corrente; depois dele, no mês seguinte. */
export const DIA_LIMITE_MES_CORRENTE = 10;

/** Mês que as telas mostram ao abrir: o corrente até o dia 10 e o seguinte a partir do dia 11. */
export function mesReferencia(hoje = new Date()): { mes: number; ano: number } {
  const avanco = hoje.getDate() > DIA_LIMITE_MES_CORRENTE ? 1 : 0;
  const base = new Date(hoje.getFullYear(), hoje.getMonth() + avanco, 1);
  return { mes: base.getMonth() + 1, ano: base.getFullYear() };
}

/** Primeiro dia do mês de referência, no fuso local. */
export function inicioMesReferencia(hoje = new Date()): Date {
  const { mes, ano } = mesReferencia(hoje);
  return new Date(ano, mes - 1, 1);
}
