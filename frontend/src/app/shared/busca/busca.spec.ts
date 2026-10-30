import { correspondeBusca, normalizarBusca } from './busca';

describe('busca', () => {
  it('ignores case and accents', () => {
    expect(normalizarBusca('  Água É ')).toBe('agua e');
    expect(correspondeBusca('agua', 'Conta de Água')).toBe(true);
    expect(correspondeBusca('ÁGUA', 'conta de agua')).toBe(true);
  });

  it('matches everything with an empty term', () => {
    expect(correspondeBusca('', 'qualquer')).toBe(true);
    expect(correspondeBusca('   ')).toBe(true);
  });

  it('requires every word somewhere in the fields', () => {
    expect(correspondeBusca('mercado nubank', 'Mercado', 'Alimentação', 'Nubank')).toBe(true);
    expect(correspondeBusca('mercado itau', 'Mercado', 'Alimentação', 'Nubank')).toBe(false);
  });

  it('matches values in the pt-BR format, with or without thousands separator', () => {
    expect(correspondeBusca('150', 'Luz', 150)).toBe(true);
    expect(correspondeBusca('150,00', 150)).toBe(true);
    expect(correspondeBusca('1.250', 1250.5)).toBe(true);
    expect(correspondeBusca('1250,5', 1250.5)).toBe(true);
    expect(correspondeBusca('-30', -30)).toBe(true);
    expect(correspondeBusca('999', 150)).toBe(false);
  });

  it('skips empty fields', () => {
    expect(correspondeBusca('null', null, undefined, 'Luz')).toBe(false);
  });
});
