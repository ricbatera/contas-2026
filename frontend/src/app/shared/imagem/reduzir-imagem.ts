/**
 * Reduz a foto para no máximo {@code ladoMaximo} pixels no maior lado e a regrava como JPEG.
 * Fotos de celular têm vários MB; reduzidas ficam abaixo de 1 MB e ainda legíveis para a IA.
 * Também converte formatos que o navegador decodifica mas o backend não aceita (ex.: HEIC no
 * Safari). A orientação EXIF é aplicada ao desenhar, para a foto não chegar deitada.
 */
export async function reduzirImagem(arquivo: Blob, ladoMaximo = 1600, qualidade = 0.85): Promise<Blob> {
  const bitmap = await createImageBitmap(arquivo, { imageOrientation: 'from-image' });
  try {
    const escala = Math.min(1, ladoMaximo / Math.max(bitmap.width, bitmap.height));
    const largura = Math.round(bitmap.width * escala);
    const altura = Math.round(bitmap.height * escala);
    const canvas = document.createElement('canvas');
    canvas.width = largura;
    canvas.height = altura;
    const contexto = canvas.getContext('2d');
    if (!contexto) {
      throw new Error('Canvas indisponível');
    }
    contexto.drawImage(bitmap, 0, 0, largura, altura);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('Não foi possível gerar a imagem'))),
        'image/jpeg',
        qualidade,
      ),
    );
  } finally {
    bitmap.close();
  }
}
