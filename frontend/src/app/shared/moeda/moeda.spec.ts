import { FormControl } from '@angular/forms';
import { formatarDigitacaoMoeda, formatarValorMoeda, paraNumeroMoeda, valorDiferenteDeZero } from './moeda';

describe('moeda', () => {
  it('formats the typed text with thousands and up to 2 decimals', () => {
    expect(formatarDigitacaoMoeda('1250,52')).toBe('1.250,52');
    expect(formatarDigitacaoMoeda('1250')).toBe('1.250');
    expect(formatarDigitacaoMoeda('1250,')).toBe('1.250,');
    expect(formatarDigitacaoMoeda('1234567,8')).toBe('1.234.567,8');
    expect(formatarDigitacaoMoeda('12,345')).toBe('12,34');
    expect(formatarDigitacaoMoeda('1.2500')).toBe('12.500');
  });

  it('drops invalid characters, extra commas and leading zeros', () => {
    expect(formatarDigitacaoMoeda('R$ 1a2,3,4')).toBe('12,34');
    expect(formatarDigitacaoMoeda('0012')).toBe('12');
    expect(formatarDigitacaoMoeda(',5')).toBe('0,5');
    expect(formatarDigitacaoMoeda('0')).toBe('0');
    expect(formatarDigitacaoMoeda('')).toBe('');
  });

  it('keeps the minus sign only when negatives are allowed', () => {
    expect(formatarDigitacaoMoeda('-1250,52', true)).toBe('-1.250,52');
    expect(formatarDigitacaoMoeda('1250-', true)).toBe('-1.250');
    expect(formatarDigitacaoMoeda('-', true)).toBe('-');
    expect(formatarDigitacaoMoeda('-1250,52')).toBe('1.250,52');
    expect(paraNumeroMoeda('-1.250,52')).toBe(-1250.52);
    expect(paraNumeroMoeda('-')).toBeNull();
    expect(formatarValorMoeda(-50)).toBe('-50,00');
  });

  it('rejects only zero in valorDiferenteDeZero', () => {
    expect(valorDiferenteDeZero(new FormControl(0))).toEqual({ zero: true });
    expect(valorDiferenteDeZero(new FormControl(-10))).toBeNull();
    expect(valorDiferenteDeZero(new FormControl(null))).toBeNull();
  });

  it('parses the field text into a number', () => {
    expect(paraNumeroMoeda('1.250,52')).toBe(1250.52);
    expect(paraNumeroMoeda('1.250,')).toBe(1250);
    expect(paraNumeroMoeda('0,5')).toBe(0.5);
    expect(paraNumeroMoeda('')).toBeNull();
    expect(paraNumeroMoeda(',')).toBeNull();
  });

  it('formats a number with 2 decimals', () => {
    expect(formatarValorMoeda(1250.5)).toBe('1.250,50');
    expect(formatarValorMoeda(0)).toBe('0,00');
  });
});
