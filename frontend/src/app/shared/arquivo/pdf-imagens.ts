/** Página do PDF: um JPEG que ocupa a folha A4 inteira. */
export interface PaginaJpeg {
  jpeg: Uint8Array;
  /** Dimensões do JPEG em pixels. */
  largura: number;
  altura: number;
}

/** A4 em pontos (1/72 pol.). */
const LARGURA_A4 = 595;
const ALTURA_A4 = 842;

/**
 * Monta um PDF em que cada página é uma imagem JPEG esticada na folha A4. Um PDF só de imagens
 * é simples o bastante para dispensar biblioteca: catálogo, árvore de páginas e, por página, o
 * objeto da página, a imagem (`DCTDecode`, os bytes do JPEG sem alteração) e o conteúdo que a desenha.
 */
export function gerarPdfDeImagens(paginas: PaginaJpeg[]): Blob {
  if (paginas.length === 0) {
    throw new Error('O PDF precisa de ao menos uma página.');
  }
  const partes: Uint8Array[] = [];
  const offsets: number[] = [];
  let tamanho = 0;
  const escrever = (parte: Uint8Array | string) => {
    const bytes = typeof parte === 'string' ? ascii(parte) : parte;
    partes.push(bytes);
    tamanho += bytes.length;
  };
  const objeto = (numero: number, ...conteudo: (Uint8Array | string)[]) => {
    offsets[numero] = tamanho;
    escrever(`${numero} 0 obj\n`);
    conteudo.forEach(escrever);
    escrever('\nendobj\n');
  };

  // O comentário com bytes acima de 127 avisa aos leitores que o arquivo é binário.
  escrever('%PDF-1.4\n');
  escrever(new Uint8Array([0x25, 0xe2, 0xe3, 0xcf, 0xd3, 0x0a]));

  // Objetos: 1 catálogo, 2 páginas; depois, por página i, 3+3i página, 4+3i imagem, 5+3i conteúdo.
  const numeroPagina = (indice: number) => 3 + indice * 3;
  objeto(1, '<< /Type /Catalog /Pages 2 0 R >>');
  const kids = paginas.map((_, indice) => `${numeroPagina(indice)} 0 R`).join(' ');
  objeto(2, `<< /Type /Pages /Kids [${kids}] /Count ${paginas.length} >>`);

  paginas.forEach((pagina, indice) => {
    const numero = numeroPagina(indice);
    objeto(numero,
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${LARGURA_A4} ${ALTURA_A4}] `
      + `/Resources << /XObject << /Im0 ${numero + 1} 0 R >> >> /Contents ${numero + 2} 0 R >>`);
    objeto(numero + 1,
      `<< /Type /XObject /Subtype /Image /Width ${pagina.largura} /Height ${pagina.altura} `
      + `/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${pagina.jpeg.length} >>\nstream\n`,
      pagina.jpeg,
      '\nendstream');
    const desenho = `q ${LARGURA_A4} 0 0 ${ALTURA_A4} 0 0 cm /Im0 Do Q`;
    objeto(numero + 2, `<< /Length ${desenho.length} >>\nstream\n${desenho}\nendstream`);
  });

  const totalObjetos = 2 + paginas.length * 3;
  const inicioXref = tamanho;
  escrever(`xref\n0 ${totalObjetos + 1}\n0000000000 65535 f \n`);
  for (let numero = 1; numero <= totalObjetos; numero++) {
    escrever(`${String(offsets[numero]).padStart(10, '0')} 00000 n \n`);
  }
  escrever(`trailer\n<< /Size ${totalObjetos + 1} /Root 1 0 R >>\nstartxref\n${inicioXref}\n%%EOF\n`);

  return new Blob(partes as BlobPart[], { type: 'application/pdf' });
}

function ascii(texto: string): Uint8Array {
  return Uint8Array.from(texto, (caractere) => caractere.charCodeAt(0));
}
