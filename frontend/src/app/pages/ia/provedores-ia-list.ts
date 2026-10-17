import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProvedorIa, ProvedorIaService, TipoProvedorIa } from '../../core/provedor-ia.service';
import { INFO_PROVEDOR, mensagemErroHttp } from './provedores-ia';

@Component({
  selector: 'app-provedores-ia-list',
  imports: [RouterLink],
  templateUrl: './provedores-ia-list.html',
  styleUrls: ['../cartoes/cartoes.css', '../contas/contas.css', './ia.css'],
})
export class ProvedoresIaList implements OnInit {
  private readonly service = inject(ProvedorIaService);

  protected readonly provedores = signal<ProvedorIa[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly deletingId = signal<string | null>(null);

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listar().subscribe({
      next: (provedores) => {
        this.provedores.set(provedores);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErroHttp(err, 'Não foi possível carregar os provedores de IA.'));
        this.loading.set(false);
      },
    });
  }

  protected nomeTipo(tipo: TipoProvedorIa): string {
    return INFO_PROVEDOR[tipo].nome;
  }

  protected excluir(provedor: ProvedorIa): void {
    if (!window.confirm(`Deseja excluir o provedor "${provedor.nome}"? Essa ação não pode ser desfeita.`)) {
      return;
    }

    this.deletingId.set(provedor.id);
    this.error.set(null);
    this.service.excluir(provedor.id).subscribe({
      next: () => {
        this.provedores.update((lista) => lista.filter((item) => item.id !== provedor.id));
        this.deletingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensagemErroHttp(err, `Não foi possível excluir o provedor "${provedor.nome}".`));
        this.deletingId.set(null);
      },
    });
  }
}
