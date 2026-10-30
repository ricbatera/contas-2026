import { vi } from 'vitest';
import { entregarArquivo } from './entregar-arquivo';

describe('entregarArquivo', () => {
  const blob = new Blob(['conteudo'], { type: 'application/pdf' });
  const navegador = navigator as Navigator & { share?: unknown; canShare?: unknown };
  const originais = { share: navegador.share, canShare: navegador.canShare };

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:arquivo');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    Object.defineProperty(navegador, 'share', { value: originais.share, configurable: true });
    Object.defineProperty(navegador, 'canShare', { value: originais.canShare, configurable: true });
    vi.restoreAllMocks();
  });

  function simularCompartilhamento(share: () => Promise<void>) {
    const compartilhar = vi.fn(share);
    Object.defineProperty(navegador, 'share', { value: compartilhar, configurable: true });
    Object.defineProperty(navegador, 'canShare', { value: () => true, configurable: true });
    return compartilhar;
  }

  it('opens the system share sheet when files can be shared', async () => {
    const compartilhar = simularCompartilhamento(() => Promise.resolve());
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click');

    await entregarArquivo(blob, 'saidas-ana-2026-11.pdf', 'Saídas de Ana');

    const [{ files, title }] = compartilhar.mock.calls[0] as unknown as [{ files: File[]; title: string }];
    expect(files[0].name).toBe('saidas-ana-2026-11.pdf');
    expect(files[0].type).toBe('application/pdf');
    expect(title).toBe('Saídas de Ana');
    expect(clique).not.toHaveBeenCalled();
  });

  it('treats a cancelled share as success, without downloading', async () => {
    simularCompartilhamento(() => Promise.reject(new DOMException('cancelado', 'AbortError')));
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click');

    await expect(entregarArquivo(blob, 'a.pdf', 'A')).resolves.toBeUndefined();
    expect(clique).not.toHaveBeenCalled();
  });

  it('downloads the file when sharing is not available', async () => {
    Object.defineProperty(navegador, 'share', { value: undefined, configurable: true });
    let link: HTMLAnchorElement | undefined;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      link = this;
    });

    await entregarArquivo(blob, 'saidas-ana-2026-11.png', 'Saídas de Ana');

    expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
    expect(link?.download).toBe('saidas-ana-2026-11.png');
    expect(link?.href).toBe('blob:arquivo');
  });
});
