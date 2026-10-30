/**
 * Entrega um arquivo gerado no navegador: no celular abre o compartilhamento do sistema
 * (WhatsApp, e-mail...) quando ele aceita arquivos; nos demais casos, baixa o arquivo.
 * Cancelar o compartilhamento não é erro.
 */
export async function entregarArquivo(blob: Blob, nomeArquivo: string, titulo: string): Promise<void> {
  const arquivo = new File([blob], nomeArquivo, { type: blob.type });
  if (typeof navigator.share === 'function' && navigator.canShare?.({ files: [arquivo] })) {
    try {
      await navigator.share({ files: [arquivo], title: titulo });
      return;
    } catch (erro) {
      if (erro instanceof DOMException && erro.name === 'AbortError') {
        return;
      }
      // Compartilhamento recusado pelo navegador (ex.: sem gesto do usuário): baixa o arquivo.
    }
  }
  baixar(blob, nomeArquivo);
}

function baixar(blob: Blob, nomeArquivo: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo;
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Dá tempo ao navegador de iniciar o download antes de liberar a URL.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
