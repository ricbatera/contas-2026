import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';
import { Home } from './pages/home/home';
import { SectionPlaceholder } from './pages/home/section-placeholder';
import { Login } from './pages/login/login';
import { Register } from './pages/register/register';

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'register', component: Register, canActivate: [guestGuard] },
  {
    path: 'home',
    component: Home,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard').then((module) => module.Dashboard),
      },
      {
        path: 'faturas',
        loadComponent: () => import('./pages/faturas/faturas').then((module) => module.Faturas),
      },
      {
        path: 'faturas/importar',
        loadComponent: () => import('./pages/faturas/fatura-importar').then((module) => module.FaturaImportar),
      },
      {
        path: 'faturas/importacoes',
        loadComponent: () =>
          import('./pages/faturas/fatura-importacoes-list').then((module) => module.FaturaImportacoesList),
      },
      {
        path: 'faturas/importacoes/:id',
        loadComponent: () =>
          import('./pages/faturas/fatura-importacao-detalhe').then((module) => module.FaturaImportacaoDetalhe),
      },
      {
        path: 'buckets',
        loadComponent: () => import('./pages/buckets/buckets-list').then((module) => module.BucketsList),
      },
      {
        path: 'buckets/novo',
        loadComponent: () => import('./pages/buckets/bucket-form').then((module) => module.BucketForm),
      },
      {
        path: 'simulacao',
        loadComponent: () => import('./pages/simulacao/simulacao').then((module) => module.Simulacao),
      },
      {
        path: 'buckets/:id/editar',
        loadComponent: () => import('./pages/buckets/bucket-form').then((module) => module.BucketForm),
      },
      {
        path: 'saidas',
        loadComponent: () => import('./pages/saidas/saidas-list').then((module) => module.SaidasList),
      },
      {
        path: 'saidas/nova',
        loadComponent: () => import('./pages/saidas/saida-form').then((module) => module.SaidaForm),
      },
      {
        path: 'saidas/:id/editar',
        loadComponent: () => import('./pages/saidas/saida-form').then((module) => module.SaidaForm),
      },
      { path: 'entradas', component: SectionPlaceholder, data: { section: 'Entradas' } },
      {
        path: 'contas',
        loadComponent: () => import('./pages/contas/contas-list').then((module) => module.ContasList),
      },
      {
        path: 'contas/novo',
        loadComponent: () => import('./pages/contas/conta-form').then((module) => module.ContaForm),
      },
      {
        path: 'contas/:id/editar',
        loadComponent: () => import('./pages/contas/conta-form').then((module) => module.ContaForm),
      },
      {
        path: 'categorias',
        loadComponent: () => import('./pages/categorias/categorias-list').then((module) => module.CategoriasList),
      },
      {
        path: 'categorias/nova',
        loadComponent: () => import('./pages/categorias/categoria-form').then((module) => module.CategoriaForm),
      },
      {
        path: 'categorias/:id/editar',
        loadComponent: () => import('./pages/categorias/categoria-form').then((module) => module.CategoriaForm),
      },
      {
        path: 'categorias/:categoriaId/subcategorias/nova',
        loadComponent: () => import('./pages/categorias/subcategoria-form').then((module) => module.SubcategoriaForm),
      },
      {
        path: 'subcategorias/:id/editar',
        loadComponent: () => import('./pages/categorias/subcategoria-form').then((module) => module.SubcategoriaForm),
      },
      {
        path: 'pessoas',
        loadComponent: () => import('./pages/pessoas/pessoas-list').then((module) => module.PessoasList),
      },
      {
        path: 'pessoas/novo',
        loadComponent: () => import('./pages/pessoas/pessoa-form').then((module) => module.PessoaForm),
      },
      {
        path: 'pessoas/:id/editar',
        loadComponent: () => import('./pages/pessoas/pessoa-form').then((module) => module.PessoaForm),
      },
      {
        path: 'cartoes',
        loadComponent: () => import('./pages/cartoes/cartoes-list').then((module) => module.CartoesList),
      },
      {
        path: 'cartoes/novo',
        loadComponent: () => import('./pages/cartoes/cartao-form').then((module) => module.CartaoForm),
      },
      {
        path: 'cartoes/:id/editar',
        loadComponent: () => import('./pages/cartoes/cartao-form').then((module) => module.CartaoForm),
      },
      { path: 'ia', pathMatch: 'full', redirectTo: 'ia/provedores' },
      {
        path: 'ia/provedores',
        loadComponent: () => import('./pages/ia/provedores-ia-list').then((module) => module.ProvedoresIaList),
      },
      {
        path: 'ia/provedores/novo',
        loadComponent: () => import('./pages/ia/provedor-ia-form').then((module) => module.ProvedorIaForm),
      },
      {
        path: 'ia/provedores/:id/editar',
        loadComponent: () => import('./pages/ia/provedor-ia-form').then((module) => module.ProvedorIaForm),
      },
      {
        path: 'ia/teste',
        loadComponent: () => import('./pages/ia/ia-teste').then((module) => module.IaTeste),
      },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
  { path: 'cartoes', pathMatch: 'full', redirectTo: 'home/cartoes' },
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  { path: '**', redirectTo: 'home' },
];
