import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';
import { PreSaida } from './pre-saida.service';

export type StatusImportacao = 'PROCESSANDO' | 'CONCLUIDA' | 'ERRO';

export interface FaturaImportacaoRequest {
  nomeArquivo: string;
  conteudoBase64: string;
  senha: string | null;
}

export interface FaturaImportacaoLista {
  id: string;
  nomeArquivo: string;
  status: StatusImportacao;
  cartaoPrincipalId: string | null;
  cartaoNome: string | null;
  cartaoUltimosDigitos: string | null;
  competencia: number | null;
  vencimento: string | null;
  totalPdf: number | null;
  preSaidasPendentes: number;
  criadoEm: string;
}

/** Situação resumida, consultada periodicamente enquanto a IA processa. */
export interface FaturaImportacaoStatus {
  id: string;
  nomeArquivo: string;
  status: StatusImportacao;
  erro: string | null;
  preSaidasPendentes: number;
  atualizadoEm: string | null;
}

export interface ItemConciliado {
  descricaoPdf: string;
  ultimosDigitos: string | null;
  valor: number;
  numeroParcela: number;
  quantidadeParcelas: number;
  saidaId: string;
  descricaoSaida: string;
  descricaoFaturaAnterior: string | null;
}

export interface ItemIgnorado {
  descricao: string;
  valor: number | null;
  tipo: string;
  motivo: string;
}

export interface ResumoImportacao {
  conciliados: ItemConciliado[];
  ignorados: ItemIgnorado[];
  avisos: string[];
  preSaidasCriadas: number;
  totalSistema: number;
  totalPreSaidas: number;
}

export interface FaturaImportacao {
  id: string;
  nomeArquivo: string;
  tamanhoBytes: number;
  status: StatusImportacao;
  erro: string | null;
  cartaoPrincipalId: string | null;
  cartaoNome: string | null;
  cartaoUltimosDigitos: string | null;
  competencia: number | null;
  vencimento: string | null;
  totalPdf: number | null;
  provedorModelo: string | null;
  criadoEm: string;
  resumo: ResumoImportacao | null;
  preSaidas: PreSaida[];
}

@Injectable({ providedIn: 'root' })
export class FaturaImportacaoService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/faturas/importacoes';

  listar(): Observable<FaturaImportacaoLista[]> {
    return this.http
      .get<ApiResponse<FaturaImportacaoLista[]>>(this.endpoint)
      .pipe(map(({ data }) => data));
  }

  buscarPorId(id: string): Observable<FaturaImportacao> {
    return this.http
      .get<ApiResponse<FaturaImportacao>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  /** Responde logo com status PROCESSANDO; a leitura por IA continua no servidor. */
  importar(request: FaturaImportacaoRequest): Observable<FaturaImportacao> {
    return this.http
      .post<ApiResponse<FaturaImportacao>>(this.endpoint, request)
      .pipe(map(({ data }) => data));
  }

  buscarStatus(id: string): Observable<FaturaImportacaoStatus> {
    return this.http
      .get<ApiResponse<FaturaImportacaoStatus>>(`${this.endpoint}/${encodeURIComponent(id)}/status`)
      .pipe(map(({ data }) => data));
  }

  /** Responde logo com status PROCESSANDO; a leitura por IA continua no servidor. */
  reprocessar(id: string): Observable<FaturaImportacao> {
    return this.http
      .post<ApiResponse<FaturaImportacao>>(`${this.endpoint}/${encodeURIComponent(id)}/reprocessar`, {})
      .pipe(map(({ data }) => data));
  }

  /** O PDF exige o token, então vem como Blob em vez de um link direto. */
  baixarPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.endpoint}/${encodeURIComponent(id)}/pdf`, { responseType: 'blob' });
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }
}

/** Lê o arquivo como data URL e devolve só o base64. */
export function lerArquivoBase64(arquivo: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const leitor = new FileReader();
    leitor.onload = () => {
      const resultado = String(leitor.result ?? '');
      resolve(resultado.substring(resultado.indexOf(',') + 1));
    };
    leitor.onerror = () => reject(leitor.error);
    leitor.readAsDataURL(arquivo);
  });
}
