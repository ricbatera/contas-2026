import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export interface DashboardResumo {
  mes: number;
  ano: number;
  gastosLancados: number;
  bucketsReservados: number;
  contasFixasMesAnterior: number;
  totalPrevisto: number;
  receitasPrevistas: number;
  receitasRecebidas: number;
  saldoPrevisto: number;
}

export interface DashboardProjecaoMes {
  mes: number;
  ano: number;
  gastosLancados: number;
  bucketsReservados: number;
  contasFixasMesAnterior: number;
  totalPrevisto: number;
  receitasPrevistas: number;
  saldoPrevisto: number;
}

export interface DashboardItem {
  descricao: string | null;
  categoriaNome: string;
  subcategoriaNome: string;
  vencimento: string | null;
  numeroParcela: number | null;
  quantidadeParcelas: number | null;
  valor: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  consultar(mes: number, ano: number): Observable<DashboardResumo> {
    const params = new HttpParams().set('mes', mes).set('ano', ano);
    return this.http.get<ApiResponse<DashboardResumo>>('/api/dashboard', { params })
      .pipe(map(({ data }) => data));
  }

  consultarProjecao(mes: number, ano: number, quantidade: number): Observable<DashboardProjecaoMes[]> {
    const params = new HttpParams()
      .set('mes', mes)
      .set('ano', ano)
      .set('quantidade', quantidade);
    return this.http.get<ApiResponse<DashboardProjecaoMes[]>>('/api/dashboard/projecao', { params })
      .pipe(map(({ data }) => data));
  }

  listarSaidas(mes: number, ano: number): Observable<DashboardItem[]> {
    const params = new HttpParams().set('mes', mes).set('ano', ano);
    return this.http.get<ApiResponse<DashboardItem[]>>('/api/dashboard/saidas', { params })
      .pipe(map(({ data }) => data));
  }

  listarBuckets(): Observable<DashboardItem[]> {
    return this.http.get<ApiResponse<DashboardItem[]>>('/api/dashboard/buckets')
      .pipe(map(({ data }) => data));
  }

  listarContasFixas(mes: number, ano: number): Observable<DashboardItem[]> {
    const params = new HttpParams().set('mes', mes).set('ano', ano);
    return this.http.get<ApiResponse<DashboardItem[]>>('/api/dashboard/contas-fixas', { params })
      .pipe(map(({ data }) => data));
  }

  listarEntradas(mes: number, ano: number): Observable<DashboardItem[]> {
    const params = new HttpParams().set('mes', mes).set('ano', ano);
    return this.http.get<ApiResponse<DashboardItem[]>>('/api/dashboard/entradas', { params })
      .pipe(map(({ data }) => data));
  }
}
