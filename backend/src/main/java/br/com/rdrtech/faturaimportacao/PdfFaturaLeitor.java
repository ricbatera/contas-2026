package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.exception.PdfInvalidoException;
import br.com.rdrtech.exception.PdfSenhaInvalidaException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Abre o PDF da fatura (com a senha, se houver), remove a proteção e extrai o texto. */
@Component
public class PdfFaturaLeitor {

    static final int TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024;

    /** Texto de cada página, na ordem do próprio PDF. */
    public record PdfLido(byte[] pdfDesbloqueado, List<String> paginas) {

        public String texto() {
            return String.join("\n", paginas);
        }
    }

    public byte[] decodificar(String conteudoBase64) {
        String conteudo = conteudoBase64.strip();
        int separador = conteudo.indexOf(',');
        if (conteudo.startsWith("data:") && separador > 0) {
            conteudo = conteudo.substring(separador + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(conteudo);
        } catch (IllegalArgumentException e) {
            throw new PdfInvalidoException("O conteúdo enviado não é um base64 válido.");
        }
        if (bytes.length == 0) {
            throw new PdfInvalidoException("O arquivo enviado está vazio.");
        }
        if (bytes.length > TAMANHO_MAXIMO_BYTES) {
            throw new PdfInvalidoException("O PDF deve ter no máximo 10 MB.");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new PdfInvalidoException("O arquivo enviado não é um PDF.");
        }
        return bytes;
    }

    public PdfLido ler(byte[] pdf, String senha) {
        try (PDDocument documento = Loader.loadPDF(pdf, senha == null ? "" : senha)) {
            byte[] desbloqueado = pdf;
            if (documento.isEncrypted()) {
                documento.setAllSecurityToBeRemoved(true);
                ByteArrayOutputStream saida = new ByteArrayOutputStream(pdf.length);
                documento.save(saida);
                desbloqueado = saida.toByteArray();
            }
            // Ordem do próprio PDF, sem ordenar por posição: faturas em duas colunas teriam as
            // colunas misturadas na mesma linha (cabeçalho de um cartão colado a compras de outro).
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            List<String> paginas = new ArrayList<>();
            for (int pagina = 1; pagina <= documento.getNumberOfPages(); pagina++) {
                stripper.setStartPage(pagina);
                stripper.setEndPage(pagina);
                paginas.add(stripper.getText(documento));
            }
            if (paginas.stream().allMatch(String::isBlank)) {
                throw new PdfInvalidoException(
                        "O PDF não contém texto legível (pode ser uma imagem digitalizada).");
            }
            return new PdfLido(desbloqueado, paginas);
        } catch (InvalidPasswordException e) {
            throw new PdfSenhaInvalidaException();
        } catch (IOException e) {
            throw new PdfInvalidoException("Não foi possível ler o PDF: arquivo corrompido ou em formato não suportado.");
        }
    }
}
