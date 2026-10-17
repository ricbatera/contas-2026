import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export type StatusPreSaida = 'PENDENTE' | 'APROVADA' | 'REJEITADA';

export interface PreSaida {
  id: string;
  importacaoId: string;
  cartaoId: string | null;
  cartaoNome: string | null;
  cartaoUltimosDigitos: string | null;
  descricao: string;
  descricaoFatura: string | null;
  dataCompra: string | null;
  valorPorParcela: number;
  numeroParcela: number;
  quantidadeParcelas: number;
  competencia: number;
  primeiroVencimento: string;
  categoriaId: string | null;
  categoriaNome: string | null;
  subcategoriaId: string | null;
  subcategoriaNome: string | null;
  status: StatusPreSaida;
  saidaId: string | null;
}

export interface PreSaidaRequest {
  descricao: string;
  cartaoId: string | null;
  categoriaId: string | null;
  subcategoriaId: string | null;
  valorPorParcela: number;
  numeroParcela: number;
  quantidadeParcelas: number;
}

export interface AprovacaoLote {
  aprovadas: PreSaida[];
  falhas: { id: string; mensagem: string }[];
}

@Injectable({ providedIn: 'root' })
export class PreSaidaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/pre-saidas';

  listar(filtro: { status?: StatusPreSaida; importacaoId?: string } = {}): Observable<PreSaida[]> {
    let params = new HttpParams();
    if (filtro.status) {
      params = params.set('status', filtro.status);
    }
    if (filtro.importacaoId) {
      params = params.set('importacaoId', filtro.importacaoId);
    }
    return this.http
      .get<ApiResponse<PreSaida[]>>(this.endpoint, { params })
      .pipe(map(({ data }) => data));
  }

  atualizar(id: string, request: PreSaidaRequest): Observable<PreSaida> {
    return this.http
      .put<ApiResponse<PreSaida>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  aprovar(id: string): Observable<PreSaida> {
    return this.acao(id, 'aprovar');
  }

  rejeitar(id: string): Observable<PreSaida> {
    return this.acao(id, 'rejeitar');
  }

  reabrir(id: string): Observable<PreSaida> {
    return this.acao(id, 'reabrir');
  }

  aprovarLote(ids: string[]): Observable<AprovacaoLote> {
    return this.http
      .post<ApiResponse<AprovacaoLote>>(`${this.endpoint}/aprovar-lote`, { ids })
      .pipe(map(({ data }) => data));
  }

  private acao(id: string, acao: string): Observable<PreSaida> {
    return this.http
      .post<ApiResponse<PreSaida>>(`${this.endpoint}/${encodeURIComponent(id)}/${acao}`, {})
      .pipe(map(({ data }) => data));
  }
}
