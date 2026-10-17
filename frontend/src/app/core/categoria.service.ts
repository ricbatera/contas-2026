import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export const TIPOS_CATEGORIA = ['DESPESA', 'RECEITA'] as const;
export type TipoCategoria = (typeof TIPOS_CATEGORIA)[number];

export interface Subcategoria {
  id: string;
  nome: string;
  ativo: boolean;
  padrao: boolean;
  categoriaId: string;
  categoriaNome: string;
  criadoEm: string | null;
}

export interface Categoria {
  id: string;
  nome: string;
  tipo: TipoCategoria;
  ativo: boolean;
  padrao: boolean;
  criadoEm: string | null;
  subcategorias: Subcategoria[];
}

export interface CategoriaRequest {
  nome: string;
  tipo: TipoCategoria;
  ativo: boolean;
}

export interface SubcategoriaRequest {
  nome: string;
  categoriaId: string;
  ativo: boolean;
  padrao: boolean;
}

export interface SubcategoriaCreateRequest {
  nome: string;
  ativo: boolean;
  padrao: boolean;
}

@Injectable({ providedIn: 'root' })
export class CategoriaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/categorias';

  listar(): Observable<Categoria[]> {
    return this.http.get<ApiResponse<Categoria[]>>(this.endpoint).pipe(map(({ data }) => data));
  }

  buscarPorId(id: string): Observable<Categoria> {
    return this.http
      .get<ApiResponse<Categoria>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  criar(request: CategoriaRequest): Observable<Categoria> {
    return this.http
      .post<ApiResponse<Categoria>>(this.endpoint, request)
      .pipe(map(({ data }) => data));
  }

  atualizar(id: string, request: CategoriaRequest): Observable<Categoria> {
    return this.http
      .put<ApiResponse<Categoria>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  listarSubcategorias(categoriaId: string): Observable<Subcategoria[]> {
    return this.http
      .get<ApiResponse<Subcategoria[]>>(
        `${this.endpoint}/${encodeURIComponent(categoriaId)}/subcategorias`,
      )
      .pipe(map(({ data }) => data));
  }

  criarSubcategoria(
    categoriaId: string,
    request: SubcategoriaCreateRequest,
  ): Observable<Subcategoria> {
    return this.http
      .post<ApiResponse<Subcategoria>>(
        `${this.endpoint}/${encodeURIComponent(categoriaId)}/subcategorias`,
        request,
      )
      .pipe(map(({ data }) => data));
  }

  buscarSubcategoria(id: string): Observable<Subcategoria> {
    return this.http
      .get<ApiResponse<Subcategoria>>(`/api/subcategorias/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  atualizarSubcategoria(id: string, request: SubcategoriaRequest): Observable<Subcategoria> {
    return this.http
      .put<ApiResponse<Subcategoria>>(`/api/subcategorias/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  excluirSubcategoria(id: string): Observable<void> {
    return this.http.delete<void>(`/api/subcategorias/${encodeURIComponent(id)}`);
  }
}
