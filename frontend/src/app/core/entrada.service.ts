import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export type StatusOcorrencia = 'PREVISTA' | 'RECEBIDA' | 'CANCELADA';

export interface EntradaRequest {
  descricao: string;
  valor: number;
  recorrente: boolean;
  dataInicial: string;
  dataFinal: string | null;
  categoriaId: string;
  subcategoriaId: string;
  contaBancariaId: string | null;
}

export interface Entrada {
  id: string;
  descricao: string;
  valor: number;
  recorrente: boolean;
  dataInicial: string;
  dataFinal: string | null;
  categoriaId: string;
  categoriaNome: string;
  subcategoriaId: string;
  subcategoriaNome: string;
  contaBancariaId: string | null;
  contaBancariaNome: string | null;
  criadoEm: string | null;
}

export interface EntradaOcorrencia {
  id: string;
  entradaId: string;
  descricao: string;
  recorrente: boolean;
  categoriaId: string;
  categoriaNome: string;
  subcategoriaId: string;
  subcategoriaNome: string;
  contaBancariaId: string | null;
  contaBancariaNome: string | null;
  competencia: string;
  dataPrevista: string;
  valorPrevisto: number;
  status: StatusOcorrencia;
  valorRecebido: number | null;
  dataRecebimento: string | null;
}

export interface ReceberOcorrenciaRequest {
  valorRecebido: number;
  dataRecebimento: string;
}

@Injectable({ providedIn: 'root' })
export class EntradaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/entradas';

  listar(): Observable<Entrada[]> {
    return this.http.get<ApiResponse<Entrada[]>>(this.endpoint).pipe(map(({ data }) => data));
  }

  buscarPorId(id: string): Observable<Entrada> {
    return this.http
      .get<ApiResponse<Entrada>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  criar(request: EntradaRequest): Observable<Entrada> {
    return this.http
      .post<ApiResponse<Entrada>>(this.endpoint, request)
      .pipe(map(({ data }) => data));
  }

  atualizar(id: string, request: EntradaRequest): Observable<Entrada> {
    return this.http
      .put<ApiResponse<Entrada>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  listarOcorrencias(mes: number, ano: number): Observable<EntradaOcorrencia[]> {
    return this.http
      .get<ApiResponse<EntradaOcorrencia[]>>(`${this.endpoint}/ocorrencias`, { params: { mes, ano } })
      .pipe(map(({ data }) => data));
  }

  receber(ocorrenciaId: string, request: ReceberOcorrenciaRequest): Observable<EntradaOcorrencia> {
    return this.acaoOcorrencia(ocorrenciaId, 'receber', request);
  }

  cancelar(ocorrenciaId: string): Observable<EntradaOcorrencia> {
    return this.acaoOcorrencia(ocorrenciaId, 'cancelar', null);
  }

  reabrir(ocorrenciaId: string): Observable<EntradaOcorrencia> {
    return this.acaoOcorrencia(ocorrenciaId, 'reabrir', null);
  }

  private acaoOcorrencia(
    ocorrenciaId: string,
    acao: 'receber' | 'cancelar' | 'reabrir',
    body: ReceberOcorrenciaRequest | null,
  ): Observable<EntradaOcorrencia> {
    return this.http
      .post<ApiResponse<EntradaOcorrencia>>(
        `${this.endpoint}/ocorrencias/${encodeURIComponent(ocorrenciaId)}/${acao}`,
        body,
      )
      .pipe(map(({ data }) => data));
  }
}
