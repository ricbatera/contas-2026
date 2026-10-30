import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export const FORMAS_PAGAMENTO = ['CARTAO_CREDITO', 'BOLETO', 'DEBITO', 'PIX'] as const;
export type FormaPagamento = (typeof FORMAS_PAGAMENTO)[number];
export type ModoResponsabilidade = 'USUARIO' | 'PESSOA' | 'DIVIDIR';

export interface SaidaParteRequest {
  pessoaId: string;
  valorPorParcela: number;
}

export interface SaidaRequest {
  descricao: string;
  descricaoFatura: string | null;
  contaFixa: boolean;
  valorPorParcela: number;
  quantidadeParcelas: number;
  formaPagamento: FormaPagamento;
  dataInicial: string | null;
  primeiroVencimento: string | null;
  cartaoId: string | null;
  categoriaId: string;
  subcategoriaId: string;
  modoResponsabilidade: ModoResponsabilidade;
  responsavelPessoaId: string | null;
  partes: SaidaParteRequest[];
}

export interface SaidaResponsavel {
  pessoaId: string | null;
  usuarioId: string | null;
  nome: string;
  valorPorParcela: number;
}

export interface SaidaParcela {
  id: string;
  numero: number;
  valor: number;
  vencimento: string;
  faturaId: string | null;
  vencimentoFatura: string | null;
}

export interface Saida {
  id: string;
  descricao: string;
  descricaoFatura: string | null;
  contaFixa: boolean;
  valorPorParcela: number;
  valorTotal: number;
  quantidadeParcelas: number;
  formaPagamento: FormaPagamento;
  modoResponsabilidade: ModoResponsabilidade;
  dataInicial: string | null;
  primeiroVencimento: string | null;
  cartaoId: string | null;
  cartaoNome: string | null;
  categoriaId: string;
  categoriaNome: string;
  subcategoriaId: string;
  subcategoriaNome: string;
  responsavelPessoaId: string | null;
  responsabilidades: SaidaResponsavel[];
  parcelas: SaidaParcela[];
  criadoEm: string | null;
  /** Liga as ocorrências mensais da mesma conta fixa. */
  serieContaFixaId?: string | null;
}

/** Conta fixa proposta para o mês, ainda não gravada; com `aviso`, ela não pode ser gerada como está. */
export interface ContaFixaPrevia {
  serieId: string;
  saidaOrigemId: string;
  descricao: string;
  categoriaNome: string;
  subcategoriaNome: string;
  formaPagamento: FormaPagamento;
  modoResponsabilidade: ModoResponsabilidade;
  responsaveis: string[];
  valorPorParcela: number;
  dataSugerida: string;
  aviso: string | null;
}

export interface GerarContasFixasRequest {
  mes: number;
  ano: number;
  itens: { serieId: string; valorPorParcela: number; data: string }[];
}

export interface GerarContasFixasResponse {
  criadas: Saida[];
  ignoradas: number;
}

@Injectable({ providedIn: 'root' })
export class SaidaService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/saidas';

  listar(): Observable<Saida[]> {
    return this.http.get<ApiResponse<Saida[]>>(this.endpoint).pipe(map(({ data }) => data));
  }

  buscarPorId(id: string): Observable<Saida> {
    return this.http
      .get<ApiResponse<Saida>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  criar(request: SaidaRequest): Observable<Saida> {
    return this.http
      .post<ApiResponse<Saida>>(this.endpoint, request)
      .pipe(map(({ data }) => data));
  }

  atualizar(id: string, request: SaidaRequest): Observable<Saida> {
    return this.http
      .put<ApiResponse<Saida>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  previaContasFixas(mes: number, ano: number): Observable<ContaFixaPrevia[]> {
    return this.http
      .get<ApiResponse<ContaFixaPrevia[]>>(`${this.endpoint}/contas-fixas/previa`, {
        params: { mes, ano },
      })
      .pipe(map(({ data }) => data));
  }

  gerarContasFixas(request: GerarContasFixasRequest): Observable<GerarContasFixasResponse> {
    return this.http
      .post<ApiResponse<GerarContasFixasResponse>>(`${this.endpoint}/contas-fixas`, request)
      .pipe(map(({ data }) => data));
  }
}
