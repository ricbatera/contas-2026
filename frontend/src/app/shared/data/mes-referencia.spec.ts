import { mesReferencia } from './mes-referencia';

describe('mesReferencia', () => {
  it('mantém o mês corrente até o dia 10', () => {
    expect(mesReferencia(new Date(2026, 9, 1))).toEqual({ mes: 10, ano: 2026 });
    expect(mesReferencia(new Date(2026, 9, 10, 23, 59))).toEqual({ mes: 10, ano: 2026 });
  });

  it('passa para o mês seguinte a partir do dia 11', () => {
    expect(mesReferencia(new Date(2026, 9, 11))).toEqual({ mes: 11, ano: 2026 });
    expect(mesReferencia(new Date(2026, 0, 31))).toEqual({ mes: 2, ano: 2026 });
  });

  it('vira o ano depois do dia 10 de dezembro', () => {
    expect(mesReferencia(new Date(2026, 11, 11))).toEqual({ mes: 1, ano: 2027 });
    expect(mesReferencia(new Date(2026, 11, 31))).toEqual({ mes: 1, ano: 2027 });
  });
});
