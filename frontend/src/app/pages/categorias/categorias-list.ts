import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  Categoria,
  CategoriaService,
  TipoCategoria,
  TIPOS_CATEGORIA,
} from '../../core/categoria.service';

@Component({
  selector: 'app-categorias-list',
  imports: [RouterLink],
  templateUrl: './categorias-list.html',
  styleUrls: ['../cartoes/cartoes.css', './categorias.css'],
})
export class CategoriasList implements OnInit {
  private readonly service = inject(CategoriaService);

  protected readonly tipos = TIPOS_CATEGORIA;
  protected readonly tipoSelecionado = signal<TipoCategoria>('DESPESA');
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly workingId = signal<string | null>(null);

  ngOnInit(): void {
    this.carregar();
  }

  protected porTipo(tipo: TipoCategoria): Categoria[] {
    return this.categorias()
      .filter((categoria) => categoria.tipo === tipo)
      .sort((a, b) => Number(b.padrao) - Number(a.padrao) || a.nome.localeCompare(b.nome, 'pt-BR'));
  }

  protected tituloTipo(tipo: TipoCategoria): string {
    return tipo === 'DESPESA' ? 'Despesas' : 'Receitas';
  }

  protected selecionarTipo(tipo: TipoCategoria): void {
    this.tipoSelecionado.set(tipo);
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listar().subscribe({
      next: (categorias) => {
        this.categorias.set(categorias);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, 'Não foi possível carregar as categorias.'));
        this.loading.set(false);
      },
    });
  }

  protected excluirCategoria(categoria: Categoria): void {
    const filhos = categoria.subcategorias.length;
    const complemento = filhos
      ? ` As ${filhos} subcategorias também serão excluídas.`
      : '';
    if (!window.confirm(`Deseja excluir "${categoria.nome}"?${complemento} Essa ação não pode ser desfeita.`)) {
      return;
    }
    this.workingId.set(categoria.id);
    this.error.set(null);
    this.service.excluir(categoria.id).subscribe({
      next: () => {
        this.categorias.update((items) => items.filter((item) => item.id !== categoria.id));
        this.workingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, `Não foi possível excluir "${categoria.nome}".`));
        this.workingId.set(null);
      },
    });
  }

  protected excluirSubcategoria(categoriaId: string, id: string, nome: string): void {
    if (!window.confirm(`Deseja excluir a subcategoria "${nome}"? Essa ação não pode ser desfeita.`)) {
      return;
    }
    this.workingId.set(id);
    this.error.set(null);
    this.service.excluirSubcategoria(id).subscribe({
      next: () => {
        this.categorias.update((items) =>
          items.map((categoria) =>
            categoria.id === categoriaId
              ? { ...categoria, subcategorias: categoria.subcategorias.filter((item) => item.id !== id) }
              : categoria,
          ),
        );
        this.workingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.mensagemErro(err, `Não foi possível excluir "${nome}".`));
        this.workingId.set(null);
      },
    });
  }

  private mensagemErro(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
    }
    return typeof err.error?.message === 'string'
      ? err.error.message
      : `${fallback} (erro ${err.status}).`;
  }
}
