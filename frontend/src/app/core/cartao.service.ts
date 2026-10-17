import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export const BANDEIRAS = ['VISA', 'MASTERCARD', 'ELO', 'AMEX', 'HIPERCARD', 'OUTRO'] as const;

export type Bandeira = (typeof BANDEIRAS)[number];
export const TIPOS_CARTAO = ['PRINCIPAL', 'ADICIONAL'] as const;
export type TipoCartao = (typeof TIPOS_CARTAO)[number];

export interface Cartao {
  id: string;
  nome: string;
  banco: string;
  bandeira: Bandeira;
  ultimosDigitos: string;
  cor: string;
  criadoEm: string;
  tipo: TipoCartao;
  virtual: boolean;
  cartaoPrincipalId: string | null;
  ativo: boolean;
}

export interface CartaoRequest {
  nome: string;
  banco: string;
  bandeira: Bandeira;
  ultimosDigitos: string;
  cor: string;
  tipo: TipoCartao;
  virtual: boolean;
  cartaoPrincipalId: string | null;
  ativo: boolean;
}

@Injectable({ providedIn: 'root' })
export class CartaoService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/cartoes';

  listar(): Observable<Cartao[]> {
    return this.http
      .get<ApiResponse<Cartao[]>>(this.endpoint)
      .pipe(map((response) => response.data));
  }

  listarPrincipais(): Observable<Cartao[]> {
    return this.http
      .get<ApiResponse<Cartao[]>>(`${this.endpoint}/principais`)
      .pipe(map((response) => response.data));
  }

  buscarPorId(id: string): Observable<Cartao> {
    return this.http
      .get<ApiResponse<Cartao>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map((response) => response.data));
  }

  criar(request: CartaoRequest): Observable<Cartao> {
    return this.http
      .post<ApiResponse<Cartao>>(this.endpoint, request)
      .pipe(map((response) => response.data));
  }

  atualizar(id: string, request: CartaoRequest): Observable<Cartao> {
    return this.http
      .put<ApiResponse<Cartao>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map((response) => response.data));
  }
}
