import { HttpErrorResponse } from '@angular/common/http';
import { StatusImportacao } from '../../core/fatura-importacao.service';

export function mensagemErro(err: HttpErrorResponse, fallback: string): string {
  if (err.status === 0) {
    return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
  }
  return typeof err.error?.message === 'string'
    ? err.error.message
    : `${fallback} (erro ${err.status}).`;
}

export function rotuloCompetencia(competencia: number | null): string {
  if (!competencia) {
    return '—';
  }
  const ano = Math.floor(competencia / 100);
  const mes = competencia % 100;
  return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })
    .format(new Date(ano, mes - 1, 1));
}

export const ROTULO_STATUS: Record<StatusImportacao, string> = {
  PROCESSANDO: 'Processando',
  CONCLUIDA: 'Concluída',
  ERRO: 'Erro na leitura',
};
