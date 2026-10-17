import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

export interface ApiResponse<T> {
  data: T;
  message?: string;
  timestamp?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface RegisterRequest {
  nome: string;
  sobrenome: string;
  email: string;
  senha: string;
  telefone?: string;
}

const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly token = signal<string | null>(this.readToken(ACCESS_KEY));

  readonly isAuthenticated = computed(() => !!this.token());

  get accessToken(): string | null {
    return this.token();
  }

  get refreshToken(): string | null {
    return this.readToken(REFRESH_KEY);
  }

  login(body: LoginRequest, remember: boolean): Observable<TokenResponse> {
    return this.http
      .post<ApiResponse<TokenResponse>>('/api/auth/login', body)
      .pipe(map((r) => r.data), tap((t) => this.store(t, remember)));
  }

  register(body: RegisterRequest, remember = true): Observable<TokenResponse> {
    return this.http
      .post<ApiResponse<TokenResponse>>('/api/auth/register', body)
      .pipe(map((r) => r.data), tap((t) => this.store(t, remember)));
  }

  refresh(): Observable<TokenResponse> {
    const refreshToken = this.refreshToken ?? '';
    const remember = !!localStorage.getItem(REFRESH_KEY);
    return this.http
      .post<ApiResponse<TokenResponse>>('/api/auth/refresh', { refreshToken })
      .pipe(map((r) => r.data), tap((t) => this.store(t, remember)));
  }

  logout(): void {
    const refreshToken = this.refreshToken;
    if (refreshToken) {
      this.http.post('/api/auth/logout', { refreshToken }).subscribe({ error: () => {} });
    }
    this.clear();
  }

  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    sessionStorage.removeItem(ACCESS_KEY);
    sessionStorage.removeItem(REFRESH_KEY);
    this.token.set(null);
  }

  private store(t: TokenResponse, remember: boolean): void {
    this.clear();
    const storage = remember ? localStorage : sessionStorage;
    storage.setItem(ACCESS_KEY, t.accessToken);
    storage.setItem(REFRESH_KEY, t.refreshToken);
    this.token.set(t.accessToken);
  }

  private readToken(key: string): string | null {
    return localStorage.getItem(key) ?? sessionStorage.getItem(key);
  }
}
