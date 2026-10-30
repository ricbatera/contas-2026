import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CampoBusca } from './campo-busca';

@Component({
  imports: [CampoBusca],
  template: `<app-campo-busca [(termo)]="busca" placeholder="Buscar pessoas" />`,
})
class Hospedeiro {
  readonly busca = signal('');
}

describe('CampoBusca', () => {
  function criar() {
    const fixture = TestBed.createComponent(Hospedeiro);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    return { fixture, element, input: element.querySelector<HTMLInputElement>('input[type="search"]')! };
  }

  it('updates the term while typing and clears it with the button', () => {
    const { fixture, element, input } = criar();
    expect(input.getAttribute('aria-label')).toBe('Buscar pessoas');
    expect(element.querySelector('button')).toBeNull();

    input.value = 'ana';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.busca()).toBe('ana');

    element.querySelector<HTMLButtonElement>('button[aria-label="Limpar busca"]')!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.busca()).toBe('');
    expect(input.value).toBe('');
  });

  it('clears the term with Escape without letting the event close a dialog', () => {
    const { fixture, input } = criar();
    fixture.componentInstance.busca.set('ana');
    fixture.detectChanges();

    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    const fechar = vi.fn();
    document.addEventListener('keydown', fechar);
    input.dispatchEvent(escape);
    document.removeEventListener('keydown', fechar);

    expect(fixture.componentInstance.busca()).toBe('');
    expect(fechar).not.toHaveBeenCalled();
  });
});
