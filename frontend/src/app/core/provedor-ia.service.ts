import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export const TIPOS_PROVEDOR_IA = ['OPENAI', 'ANTHROPIC', 'GEMINI', 'OLLAMA'] as const;
export type TipoProvedorIa = (typeof TIPOS_PROVEDOR_IA)[number];

export interface ProvedorIa {
  id: string;
  nome: string;
  tipo: TipoProvedorIa;
  modelo: string;
  baseUrl: string | null;
  possuiApiKey: boolean;
  apiKeyMascarada: string | null;
  temperatura: number | null;
  maxTokens: number | null;
  timeoutSegundos: number;
  padrao: boolean;
  ativo: boolean;
  criadoEm: string | null;
}

export interface ProvedorIaRequest {
  nome: string;
  tipo: TipoProvedorIa;
  modelo: string;
  baseUrl: string | null;
  /** Vazio na edição mantém a chave já gravada. */
  apiKey: string | null;
  temperatura: number | null;
  maxTokens: number | null;
  timeoutSegundos: number;
  padrao: boolean;
  ativo: boolean;
}

export interface TesteIa {
  sucesso: boolean;
  prompt: string;
  resposta: string | null;
  modelo: string | null;
  latenciaMs: number;
  tokensEntrada: number | null;
  tokensSaida: number | null;
  erro: string | null;
}

export interface ModeloIa {
  nome: string;
  nomeExibicao: string;
}

export interface ModelosIaRequest {
  tipo: TipoProvedorIa;
  baseUrl: string | null;
  /** Vazio com `provedorId` usa a chave já gravada no provedor. */
  apiKey: string | null;
  provedorId: string | null;
}

export interface ModelosIa {
  modelos: ModeloIa[];
  erro: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProvedorIaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/ia/provedores';

  listar(): Observable<ProvedorIa[]> {
    return this.http
      .get<ApiResponse<ProvedorIa[]>>(this.endpoint)
      .pipe(map((response) => response.data));
  }

  buscarPorId(id: string): Observable<ProvedorIa> {
    return this.http
      .get<ApiResponse<ProvedorIa>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map((response) => response.data));
  }

  criar(request: ProvedorIaRequest): Observable<ProvedorIa> {
    return this.http
      .post<ApiResponse<ProvedorIa>>(this.endpoint, request)
      .pipe(map((response) => response.data));
  }

  atualizar(id: string, request: ProvedorIaRequest): Observable<ProvedorIa> {
    return this.http
      .put<ApiResponse<ProvedorIa>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map((response) => response.data));
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  listarModelos(request: ModelosIaRequest): Observable<ModelosIa> {
    return this.http
      .post<ApiResponse<ModelosIa>>(`${this.endpoint}/modelos`, request)
      .pipe(map((response) => response.data));
  }

  testar(id: string, prompt: string | null): Observable<TesteIa> {
    return this.http
      .post<ApiResponse<TesteIa>>(`${this.endpoint}/${encodeURIComponent(id)}/testar`, { prompt })
      .pipe(map((response) => response.data));
  }
}
