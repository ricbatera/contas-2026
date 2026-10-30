import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AvisoFormulario } from './aviso-formulario';

@Component({
  imports: [AvisoFormulario, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="enviados = enviados + 1" novalidate>
      <label class="form-field"><span>Nome</span><input id="nome" formControlName="nome" /></label>
      <label class="form-field"><span>E-mail</span><input id="email" formControlName="email" /></label>
      <app-aviso-formulario />
      <button type="submit">Salvar</button>
    </form>
  `,
})
class HostTeste {
  enviados = 0;
  readonly form = new FormGroup({
    nome: new FormControl('', Validators.required),
    email: new FormControl('', [Validators.required, Validators.email]),
  });
}

describe('AvisoFormulario', () => {
  let fixture: ComponentFixture<HostTeste>;
  let rolar: ReturnType<typeof vi.fn>;
  let element: HTMLElement;

  beforeEach(() => {
    rolar = vi.fn();
    Element.prototype.scrollIntoView = rolar as unknown as Element['scrollIntoView'];
    fixture = TestBed.createComponent(HostTeste);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
    document.body.appendChild(element);
  });

  afterEach(() => {
    element.remove();
    delete (Element.prototype as Partial<Element>).scrollIntoView;
  });

  async function salvar(): Promise<void> {
    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function aviso(): string | null {
    return element.querySelector('[role="alert"]')?.textContent?.trim() ?? null;
  }

  function digitar(id: string, texto: string): void {
    const input = element.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = texto;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('warns about required fields and scrolls to the first invalid one', async () => {
    expect(aviso()).toBeNull();
    await salvar();

    expect(aviso()).toBe('Preencha os campos obrigatórios destacados.');
    const nome = element.querySelector<HTMLInputElement>('#nome')!;
    expect(rolar).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
    expect(rolar.mock.contexts[0]).toBe(nome);
    expect(document.activeElement).toBe(nome);
    expect(nome.classList).toContain('ng-touched');
  });

  it('switches to a format message and hides once the form is valid', async () => {
    digitar('nome', 'Maria');
    digitar('email', 'invalido');
    await salvar();
    expect(aviso()).toBe('Corrija os campos destacados.');
    expect(rolar.mock.contexts[0]).toBe(element.querySelector('#email'));

    digitar('email', 'maria@exemplo.com');
    expect(aviso()).toBeNull();
  });

  it('does nothing when the form is valid', async () => {
    digitar('nome', 'Maria');
    digitar('email', 'maria@exemplo.com');
    await salvar();
    expect(aviso()).toBeNull();
    expect(rolar).not.toHaveBeenCalled();
    expect(fixture.componentInstance.enviados).toBe(1);
  });
});
