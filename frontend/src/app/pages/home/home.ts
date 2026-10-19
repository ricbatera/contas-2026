import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, ApiResponse } from '../../core/auth.service';
import { ImportacaoMonitorService } from '../../core/importacao-monitor.service';
import { ImportacaoAlertas } from './importacao-alertas';

interface UserProfile {
  nome: string;
  sobrenome: string;
}

const SECTIONS = [
  { name: 'Dashboard', icon: 'dashboard', path: '/home/dashboard' },
  { name: 'Fatura', icon: 'invoice', path: '/home/faturas' },
  { name: 'Comprovantes', icon: 'camera', path: '/home/comprovantes' },
  { name: 'Buckets', icon: 'buckets', path: '/home/buckets' },
  { name: 'Simulação', icon: 'simulation', path: '/home/simulacao' },
  { name: 'Saídas', icon: 'arrow-up', path: '/home/saidas' },
  { name: 'Entradas', icon: 'arrow-down', path: '/home/entradas' },
  { name: 'Cartões', icon: 'card', path: '/home/cartoes' },
  { name: 'Contas', icon: 'wallet', path: '/home/contas' },
  { name: 'Pessoas', icon: 'people', path: '/home/pessoas' },
  { name: 'Categorias', icon: 'categories', path: '/home/categorias' },
  { name: 'IA', icon: 'ai', path: '/home/ia' },
] as const;

@Component({
  selector: 'app-home',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, ImportacaoAlertas],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly monitor = inject(ImportacaoMonitorService);

  protected readonly sections = SECTIONS;
  protected readonly collapsed = signal(window.innerWidth <= 760);
  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly profileError = signal(false);
  private readonly now = signal(new Date());
  protected readonly dateTime = computed(() =>
    new Intl.DateTimeFormat('pt-BR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(this.now()),
  );

  constructor() {
    const timer = window.setInterval(() => this.now.set(new Date()), 1000);
    this.destroyRef.onDestroy(() => window.clearInterval(timer));

    // Acompanha faturas sendo lidas pela IA em segundo plano, inclusive após recarregar a página.
    this.monitor.iniciar();
    this.destroyRef.onDestroy(() => this.monitor.parar());

    this.http.get<ApiResponse<UserProfile>>('/api/usuarios/me').subscribe({
      next: ({ data }) => this.profile.set(data),
      error: () => this.profileError.set(true),
    });
  }

  protected toggleSidebar(): void {
    this.collapsed.update((value) => !value);
  }

  protected closeSidebarOnMobile(): void {
    if (window.innerWidth <= 760) {
      this.collapsed.set(true);
    }
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
