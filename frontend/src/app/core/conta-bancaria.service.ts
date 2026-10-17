import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export const TIPOS_CONTA = ['CORRENTE', 'POUPANCA', 'CARTEIRA', 'INVESTIMENTO', 'OUTRO'] as const;
export type TipoConta = (typeof TIPOS_CONTA)[number];

export interface ContaBancaria {
  id: string;
  nome: string;
  banco: string;
  tipoConta: TipoConta;
  agencia: string | null;
  numeroConta: string | null;
  saldoInicial: number;
  cor: string | null;
  ativo: boolean;
  criadoEm: string | null;
}

export interface ContaBancariaRequest {
  nome: string;
  banco: string;
  tipoConta: TipoConta;
  agencia: string | null;
  numeroConta: string | null;
  saldoInicial: number;
  cor: string | null;
  ativo: boolean;
}

@Injectable({ providedIn: 'root' })
export class ContaBancariaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/contas-bancarias';

  listar(): Observable<ContaBancaria[]> {
    return this.http
      .get<ApiResponse<ContaBancaria[]>>(this.endpoint)
      .pipe(map((response) => response.data));
  }

  buscarPorId(id: string): Observable<ContaBancaria> {
    return this.http
      .get<ApiResponse<ContaBancaria>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map((response) => response.data));
  }

  criar(request: ContaBancariaRequest): Observable<ContaBancaria> {
    return this.http
      .post<ApiResponse<ContaBancaria>>(this.endpoint, request)
      .pipe(map((response) => response.data));
  }

  atualizar(id: string, request: ContaBancariaRequest): Observable<ContaBancaria> {
    return this.http
      .put<ApiResponse<ContaBancaria>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map((response) => response.data));
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }
}
