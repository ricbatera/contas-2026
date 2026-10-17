import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export interface Pessoa {
  id: string;
  nome: string;
  ativo: boolean;
  criadoEm: string | null;
}

export interface PessoaRequest {
  nome: string;
  ativo: boolean;
}

@Injectable({ providedIn: 'root' })
export class PessoaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/pessoas';

  listar(): Observable<Pessoa[]> {
    return this.http.get<ApiResponse<Pessoa[]>>(this.endpoint).pipe(map(({ data }) => data));
  }

  buscarPorId(id: string): Observable<Pessoa> {
    return this.http
      .get<ApiResponse<Pessoa>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  criar(request: PessoaRequest): Observable<Pessoa> {
    return this.http
      .post<ApiResponse<Pessoa>>(this.endpoint, request)
      .pipe(map(({ data }) => data));
  }

  atualizar(id: string, request: PessoaRequest): Observable<Pessoa> {
    return this.http
      .put<ApiResponse<Pessoa>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }
}
