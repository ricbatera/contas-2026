import { avaliarExpressao } from './expressao';

describe('avaliarExpressao', () => {
  it('evaluates arithmetic with precedence and parentheses', () => {
    expect(avaliarExpressao('2 + 3 * 4')).toBe(14);
    expect(avaliarExpressao('(2 + 3) * 4')).toBe(20);
    expect(avaliarExpressao('10 - 4 - 3')).toBe(3);
    expect(avaliarExpressao('100 / 4 / 5')).toBe(5);
  });

  it('accepts comma or dot decimals and the keypad symbols', () => {
    expect(avaliarExpressao('10+5,5')).toBe(15.5);
    expect(avaliarExpressao('1.25 × 4')).toBe(5);
    expect(avaliarExpressao('9 ÷ 2 − 0,5')).toBe(4);
    expect(avaliarExpressao(',5 + 5,')).toBe(5.5);
  });

  it('supports unary minus and rounds to cents', () => {
    expect(avaliarExpressao('-5 + 2')).toBe(-3);
    expect(avaliarExpressao('3 * -(1 + 1)')).toBe(-6);
    expect(avaliarExpressao('350 / 3')).toBe(116.67);
    expect(Object.is(avaliarExpressao('-0,001'), -0)).toBe(false);
  });

  it('returns null for invalid expressions', () => {
    for (const texto of ['', '   ', '2+', '1/0', 'abc', '(1+2', '1+2)', '2 3', '1,2,3']) {
      expect(avaliarExpressao(texto)).toBeNull();
    }
  });
});
