import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export interface FaturaCompetencia {
  competencia: number;
  vencimento: string;
  total: number;
}

export interface FaturaResponsavel {
  pessoaId: string | null;
  usuarioId: string | null;
  nome: string;
  valorPorParcela: number;
}

export interface FaturaParcela {
  saidaId: string;
  descricao: string;
  descricaoFatura: string | null;
  cartaoId: string | null;
  cartaoNome: string | null;
  numeroParcela: number;
  quantidadeParcelas: number;
  vencimento: string;
  valor: number;
  responsaveis: FaturaResponsavel[];
}

export interface Fatura {
  cartaoId: string;
  cartaoNome: string;
  banco: string;
  competencia: number;
  vencimento: string;
  total: number;
  parcelas: FaturaParcela[];
}

@Injectable({ providedIn: 'root' })
export class FaturaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/faturas/cartoes';

  listarCompetencias(cartaoId: string): Observable<FaturaCompetencia[]> {
    return this.http
      .get<ApiResponse<FaturaCompetencia[]>>(
        `${this.endpoint}/${encodeURIComponent(cartaoId)}/competencias`,
      )
      .pipe(map(({ data }) => data));
  }

  buscar(cartaoId: string, competencia: number): Observable<Fatura> {
    return this.http
      .get<ApiResponse<Fatura>>(
        `${this.endpoint}/${encodeURIComponent(cartaoId)}/${competencia}`,
      )
      .pipe(map(({ data }) => data));
  }
}
