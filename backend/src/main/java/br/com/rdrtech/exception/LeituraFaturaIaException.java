package br.com.rdrtech.exception;

import lombok.Getter;

/**
 * A IA não devolveu uma leitura aproveitável da fatura. Carrega as respostas recebidas até a
 * falha, para ficarem gravadas na importação e o erro poder ser diagnosticado.
 */
@Getter
public class LeituraFaturaIaException extends FaturaImportacaoInvalidaException {

    private final String respostaBruta;
    private final String modelo;

    public LeituraFaturaIaException(String message, String respostaBruta, String modelo) {
        super(message);
        this.respostaBruta = respostaBruta;
        this.modelo = modelo;
    }
}
