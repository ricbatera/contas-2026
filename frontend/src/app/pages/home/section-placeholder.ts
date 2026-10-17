import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-section-placeholder',
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">VISÃO GERAL</p>
        <h1>{{ title() }}</h1>
      </div>
    </div>
    <section class="placeholder-card" aria-live="polite">
      <div class="placeholder-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M3 3h8v8H3zm10 0h8v5h-8zm0 7h8v11h-8zM3 13h8v8H3z" />
        </svg>
      </div>
      <h2>{{ title() }}</h2>
      <p>O conteúdo desta seção estará disponível em breve.</p>
    </section>
  `,
  styles: `
    .page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 27px; }
    .eyebrow { margin: 0 0 5px; color: #8a96a8; font-size: 10px; font-weight: 700; letter-spacing: 1.2px; }
    h1 { margin: 0; color: #172b4d; font-size: 28px; font-weight: 650; letter-spacing: -.6px; }
    .placeholder-card { display: flex; min-height: 330px; flex-direction: column; align-items: center; justify-content: center; padding: 32px; border: 1px solid #e9edf3; border-radius: 14px; background: #fff; box-shadow: 0 4px 16px rgb(24 45 80 / 3%); text-align: center; }
    .placeholder-icon { display: grid; width: 56px; height: 56px; place-items: center; border-radius: 16px; color: #1e6fd0; background: #edf5ff; }
    .placeholder-icon svg { width: 24px; height: 24px; fill: currentColor; }
    h2 { margin: 18px 0 6px; color: #26364d; font-size: 18px; font-weight: 600; }
    .placeholder-card p { margin: 0; color: #8995a7; font-size: 13px; }
    @media (max-width: 760px) { h1 { font-size: 24px; } .placeholder-card { min-height: 260px; padding: 22px; } }
  `,
})
export class SectionPlaceholder {
  private readonly route = inject(ActivatedRoute);
  protected readonly title = signal(String(this.route.snapshot.data['section'] ?? 'Dashboard'));
}
