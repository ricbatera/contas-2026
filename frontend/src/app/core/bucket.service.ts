import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export interface Bucket {
  id: string;
  categoriaId: string;
  categoriaNome: string;
  subcategoriaId: string;
  subcategoriaNome: string;
  responsavelPessoaId: string | null;
  responsavelNome: string;
  responsavelUsuario: boolean;
  valorMensal: number;
  valorConsumido: number;
  saldo: number;
  percentualConsumido: number;
  mes: number;
  ano: number;
}

export interface BucketRequest {
  subcategoriaId: string;
  responsavelPessoaId: string | null;
  valorMensal: number;
}

@Injectable({ providedIn: 'root' })
export class BucketService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/buckets';

  listar(mes: number, ano: number): Observable<Bucket[]> {
    const params = new HttpParams().set('mes', mes).set('ano', ano);
    return this.http.get<ApiResponse<Bucket[]>>(this.endpoint, { params }).pipe(map(({ data }) => data));
  }

  buscarPorId(id: string): Observable<Bucket> {
    return this.http.get<ApiResponse<Bucket>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  criar(request: BucketRequest): Observable<Bucket> {
    return this.http.post<ApiResponse<Bucket>>(this.endpoint, request).pipe(map(({ data }) => data));
  }

  atualizar(id: string, request: BucketRequest): Observable<Bucket> {
    return this.http.put<ApiResponse<Bucket>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }
}
