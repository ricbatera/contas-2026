import { gerarPdfDeImagens } from './pdf-imagens';

/** Bytes do Blob como texto latin1, para inspecionar a estrutura do PDF. */
async function lerComoTexto(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  return Array.from(bytes, (byte) => String.fromCharCode(byte)).join('');
}

describe('gerarPdfDeImagens', () => {
  const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0xff, 0xd9]);

  it('builds a valid PDF with one A4 page per image', async () => {
    const blob = gerarPdfDeImagens([
      { jpeg, largura: 1240, altura: 1754 },
      { jpeg, largura: 1240, altura: 1754 },
    ]);
    const texto = await lerComoTexto(blob);

    expect(blob.type).toBe('application/pdf');
    expect(texto.startsWith('%PDF-1.4\n')).toBe(true);
    expect(texto.trimEnd().endsWith('%%EOF')).toBe(true);
    expect(texto).toContain('/Count 2');
    expect(texto.match(/\/Type \/Page /g)).toHaveLength(2);
    expect(texto.match(/\/Filter \/DCTDecode \/Length 8 /g)).toHaveLength(2);
    expect(texto).toContain('/MediaBox [0 0 595 842]');
    expect(texto).toContain('/Width 1240 /Height 1754');
  });

  it('points every xref entry and startxref to the right offsets', async () => {
    const texto = await lerComoTexto(gerarPdfDeImagens([{ jpeg, largura: 10, altura: 10 }]));

    const inicioXref = Number(/startxref\n(\d+)/.exec(texto)![1]);
    expect(texto.slice(inicioXref, inicioXref + 4)).toBe('xref');
    const entradas = texto.slice(inicioXref).match(/^(\d{10}) 00000 n $/gm)!;
    expect(entradas).toHaveLength(5);
    entradas.forEach((entrada, indice) => {
      const offset = Number(entrada.slice(0, 10));
      expect(texto.startsWith(`${indice + 1} 0 obj\n`, offset)).toBe(true);
    });
    expect(texto).toContain('/Size 6');
  });

  it('refuses an empty document', () => {
    expect(() => gerarPdfDeImagens([])).toThrow();
  });
});
