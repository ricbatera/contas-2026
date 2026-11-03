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
  diaFechamento?: number | null;
  diaVencimento?: number | null;
  /** Último dia de uso de um cartão virtual temporário (yyyy-MM-dd). */
  validoAte?: string | null;
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
  diaFechamento?: number | null;
  diaVencimento?: number | null;
  /** Último dia de uso de um cartão virtual temporário (yyyy-MM-dd). */
  validoAte?: string | null;
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

/** Data local em yyyy-MM-dd, no formato de `validoAte`. */
function dataIso(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

/**
 * O cartão pode ser usado na data (padrão: hoje)? Cartões sem validade sempre podem; os virtuais
 * temporários só até `validoAte`. `data` aceita yyyy-MM-dd; vazia conta como vigente.
 */
export function cartaoVigente(cartao: Pick<Cartao, 'validoAte'>, data: string | Date | null = new Date()): boolean {
  if (!cartao.validoAte || data === null || data === '') {
    return true;
  }
  const referencia = typeof data === 'string' ? data.slice(0, 10) : dataIso(data);
  return referencia <= cartao.validoAte;
}
