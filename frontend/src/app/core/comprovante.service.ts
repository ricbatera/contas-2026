import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export type StatusComprovante = 'PENDENTE' | 'APROVADO' | 'REJEITADO';
export type ModalidadeComprovante = 'CREDITO' | 'DEBITO';

export interface ComprovanteRequest {
  nomeArquivo: string;
  conteudoBase64: string;
}

export interface ComprovanteEdicaoRequest {
  modalidade: ModalidadeComprovante;
  descricao: string | null;
  cartaoId: string | null;
  categoriaId: string | null;
  subcategoriaId: string | null;
  dataCompra: string | null;
  valorTotal: number | null;
  quantidadeParcelas: number;
  primeiroVencimento: string | null;
}

export interface Comprovante {
  id: string;
  nomeArquivo: string;
  tipoMidia: string;
  tamanhoBytes: number;
  status: StatusComprovante;
  erroLeitura: string | null;
  avisos: string[];
  estabelecimento: string | null;
  ultimosDigitosLidos: string | null;
  bandeiraLida: string | null;
  provedorModelo: string | null;
  modalidade: ModalidadeComprovante;
  descricao: string | null;
  dataCompra: string | null;
  valorTotal: number | null;
  quantidadeParcelas: number;
  valorPorParcela: number | null;
  primeiroVencimento: string | null;
  cartaoId: string | null;
  cartaoNome: string | null;
  cartaoUltimosDigitos: string | null;
  categoriaId: string | null;
  categoriaNome: string | null;
  subcategoriaId: string | null;
  subcategoriaNome: string | null;
  saidaId: string | null;
  criadoEm: string;
}

export interface ComprovanteLista {
  id: string;
  status: StatusComprovante;
  modalidade: ModalidadeComprovante;
  descricao: string | null;
  estabelecimento: string | null;
  dataCompra: string | null;
  valorTotal: number | null;
  quantidadeParcelas: number;
  cartaoId: string | null;
  cartaoNome: string | null;
  cartaoUltimosDigitos: string | null;
  erroLeitura: string | null;
  criadoEm: string;
}

@Injectable({ providedIn: 'root' })
export class ComprovanteService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/comprovantes';

  listar(status?: StatusComprovante): Observable<ComprovanteLista[]> {
    const params: Record<string, string> = status ? { status } : {};
    return this.http
      .get<ApiResponse<ComprovanteLista[]>>(this.endpoint, { params })
      .pipe(map(({ data }) => data));
  }

  buscarPorId(id: string): Observable<Comprovante> {
    return this.http
      .get<ApiResponse<Comprovante>>(`${this.endpoint}/${encodeURIComponent(id)}`)
      .pipe(map(({ data }) => data));
  }

  /** A imagem exige o token, então vem como Blob em vez de um link direto. */
  baixarImagem(id: string): Observable<Blob> {
    return this.http.get(`${this.endpoint}/${encodeURIComponent(id)}/imagem`, { responseType: 'blob' });
  }

  /** Envia a foto e devolve o comprovante já lido pela IA. */
  enviar(request: ComprovanteRequest): Observable<Comprovante> {
    return this.http
      .post<ApiResponse<Comprovante>>(this.endpoint, request)
      .pipe(map(({ data }) => data));
  }

  reler(id: string): Observable<Comprovante> {
    return this.acao(id, 'reler');
  }

  atualizar(id: string, request: ComprovanteEdicaoRequest): Observable<Comprovante> {
    return this.http
      .put<ApiResponse<Comprovante>>(`${this.endpoint}/${encodeURIComponent(id)}`, request)
      .pipe(map(({ data }) => data));
  }

  aprovar(id: string): Observable<Comprovante> {
    return this.acao(id, 'aprovar');
  }

  rejeitar(id: string): Observable<Comprovante> {
    return this.acao(id, 'rejeitar');
  }

  reabrir(id: string): Observable<Comprovante> {
    return this.acao(id, 'reabrir');
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  private acao(id: string, acao: string): Observable<Comprovante> {
    return this.http
      .post<ApiResponse<Comprovante>>(`${this.endpoint}/${encodeURIComponent(id)}/${acao}`, {})
      .pipe(map(({ data }) => data));
  }
}

/**
 * Vencimento da fatura em que a compra entra, pelos dias do cartão principal (mesma regra do
 * backend): compras a partir do fechamento vão para a fatura seguinte, e o vencimento cai no mês
 * do fechamento quando o dia de vencimento é maior; senão, no mês seguinte.
 */
export function calcularVencimento(dataCompra: string, diaFechamento: number, diaVencimento: number): string {
  const [ano, mes, dia] = dataCompra.split('-').map(Number);
  const diaNoMes = (a: number, m: number, d: number) => Math.min(d, new Date(a, m + 1, 0).getDate());
  let mesFechamento = mes - 1;
  if (dia >= diaNoMes(ano, mesFechamento, diaFechamento)) {
    mesFechamento += 1;
  }
  const mesVencimento = diaVencimento > diaFechamento ? mesFechamento : mesFechamento + 1;
  const base = new Date(ano, mesVencimento, 1);
  const resultado = new Date(base.getFullYear(), base.getMonth(), diaNoMes(base.getFullYear(), base.getMonth(), diaVencimento));
  const doisDigitos = (n: number) => String(n).padStart(2, '0');
  return `${resultado.getFullYear()}-${doisDigitos(resultado.getMonth() + 1)}-${doisDigitos(resultado.getDate())}`;
}
