import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ImportacaoMonitorService } from '../../core/importacao-monitor.service';

/** Alertas flutuantes do processamento de faturas: em andamento, concluída ou com erro. */
@Component({
  selector: 'app-importacao-alertas',
  templateUrl: './importacao-alertas.html',
  styleUrl: './importacao-alertas.css',
})
export class ImportacaoAlertas {
  private readonly router = inject(Router);
  protected readonly monitor = inject(ImportacaoMonitorService);

  protected abrir(id: string): void {
    this.monitor.dispensar(id);
    this.router.navigate(['/home/faturas/importacoes', id]);
  }
}
