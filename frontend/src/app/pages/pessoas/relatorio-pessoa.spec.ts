import { nomeArquivoRelatorio, paginar } from './relatorio-pessoa';

describe('relatorio-pessoa', () => {
  describe('paginar', () => {
    it('keeps everything on one page when it fits', () => {
      expect(paginar([100, 200, 300], 600)).toEqual([[0, 1, 2]]);
    });

    it('moves a block that does not fit to the next page, never splitting it', () => {
      expect(paginar([300, 200, 200, 200], 600)).toEqual([[0, 1], [2, 3]]);
    });

    it('reserves room for the repeated header on the following pages', () => {
      // Na 2ª página sobram 600 - 100 = 500: cabem dois blocos de 200, não três.
      expect(paginar([600, 200, 200, 200], 600, 100)).toEqual([[0], [1, 2], [3]]);
    });

    it('puts a block taller than the page alone on its page', () => {
      expect(paginar([100, 900, 100], 600)).toEqual([[0], [1], [2]]);
    });
  });

  it('builds a file name without accents or spaces', () => {
    expect(nomeArquivoRelatorio('Ana Maria Conceição', 11, 2026, 'pdf')).toBe('saidas-ana-maria-conceicao-2026-11.pdf');
    expect(nomeArquivoRelatorio('João', 3, 2027, 'png')).toBe('saidas-joao-2027-03.png');
    expect(nomeArquivoRelatorio('***', 1, 2026, 'png')).toBe('saidas-pessoa-2026-01.png');
  });
});
